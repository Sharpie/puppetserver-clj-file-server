(ns puppetlabs.services.file-serving.file-serving-core
  (:require
   [clojure.java.io :as io]
   [puppetlabs.comidi :as comidi]
   [puppetlabs.ring-middleware.utils :as request-utils]
   [puppetlabs.bodgery.jruby :as jruby]
   [ring.middleware.params :as params]
   [ring.util.response :as response])
  (:import
   (java.nio.file Files FileVisitResult FileVisitOption LinkOption SimpleFileVisitor)
   (org.apache.commons.codec.digest DigestUtils)
   (org.apache.commons.io.input BoundedInputStream)
   (org.eclipse.jetty.servlet ServletContextHandler ServletHolder DefaultServlet)
   (org.eclipse.jetty.util.resource ResourceCollection)))

(defn build-mock-file-handler
  "Creates a Jetty ResourceCollection which serves files from multiple
  directories specified by the paths argument. The handler will mount
  at the route given by context-path."
  [context-path paths]
  (let [handler (ServletContextHandler. nil context-path ServletContextHandler/NO_SESSIONS)
        servlet (ServletHolder. (DefaultServlet.))
        resource-collection (ResourceCollection. (into-array String paths))]

    (.setBaseResource handler resource-collection)
    (.addServlet handler servlet "/")

    handler))

(defn hello
  "Say hello to caller"
  [caller]
  (format "Hello, %s!" caller))


;; Admin API

(defn refresh-jruby-state!
  "Refreshes the state of environments and mount points visible to the Ruby
  layer."
  [context]
  (let [jruby-service (:jruby-service context)]
    (reset! (:environments context)
            (jruby/run-script! jruby-service "file_serving_shims/get_environments.rb"))
    (reset! (:ruby-mounts context)
            (jruby/run-script! jruby-service "file_serving_shims/get_mounts.rb"))))

(defn file-serving-info-handler
  "Build a Ring handler for returning the state of the file serving system."
  [context]
  (fn [request]
    (request-utils/json-response
      200
      {:environments (-> context :environments deref)
       :ruby_mounts (-> context :ruby-mounts deref)})))

(defn file-serving-refresh-handler
  "Builds a Ring handler that re-syncs the file server state with the
  current state of the JRuby pool."
  [context]
  (fn [request]
    (refresh-jruby-state! context)
    {:status 204}))

(defn admin-handler
  "Returns a Ring handler for inspecting and flushing the state of
  environments and mount points"
  [context]
  (let [info-handler (file-serving-info-handler context)
        refresh-handler (file-serving-refresh-handler context)]
    (comidi/routes->handler
      (comidi/routes
        (comidi/GET "/file-serving" request
                    (info-handler request))
        (comidi/DELETE "/file-serving" request
                       (refresh-handler request))))))


;; File Utilities

(def links-follow
  "Functions in the java.nio namespace default to following links,
  so an empty array of options suffices."
  (into-array LinkOption []))

(def links-nofollow
  "An array of LinkOptions which prevents symlinks from being followed."
  (into-array LinkOption [LinkOption/NOFOLLOW_LINKS]))

(def unix-attributes
  "A string of 'Unix' FileAttributes to read. Oddly, these are not in the
  official Java 8 docs. However, they work and seem to be the quickest
  means of getting items like numeric uid, gid and file modes."
  "unix:mode,uid,gid,creationTime,lastModifiedTime,isDirectory,isRegularFile,isSymbolicLink")

(defn file-attributes
  "Takes a string path and determines file information such as ownership,
  access times and permissions."
  ([path]
   (file-attributes path false))
  ([path follow-links?]
   (let [path (-> path io/as-file .toPath)
         link-behavior (if follow-links? links-follow links-nofollow)]
     (Files/readAttributes path unix-attributes link-behavior))))

(defn attributes->mode
  "Extracts a file mode from a Files/readAttributes result."
  [attributes]
  (-> attributes
      (get "mode")
      (bit-and 07777)))

(defn attributes->type
  "Extracts the file type from a Files/readAttributes result."
  [attributes]
  (cond
    (get attributes "isRegularFile") :file
    (get attributes "isDirectory") :directory
    (get attributes "isSymbolicLink") :link
    :else :other))

(defn readlink
  [path]
  (-> path
      io/as-file
      .toPath
      Files/readSymbolicLink
      .toString))

;; TODO: Figure out how to handle "lite" versions of each hash function.
(defn file-digest
  [path checksum-type lite?]
  (with-open [input (if lite?
                      ;; Lite versions checksum just the first 512 bytes.
                      (-> path io/input-stream (BoundedInputStream. 512))
                      (-> path io/input-stream))]
    (case checksum-type
      "md5" (DigestUtils/md5Hex input)
      "sha1" (DigestUtils/sha1Hex input)
      "sha256" (DigestUtils/sha256Hex input))))

(defn file-checksum
  [path attributes checksum-type]
  (case checksum-type
    "none" {:type "none"
            :value "{none}"}
    "ctime" {:type "ctime"
             :value (str "{ctime}" (get attributes "creationTime"))}
    "mtime" {:type "mtime"
             :value (str "{mtime}" (get attributes "lastModifiedTime"))}
    "md5" {:type "md5"
           :value (str "{md5}" (file-digest path "md5" false))}
    "md5lite" {:type "md5lite"
               :value (str "{md5lite}" (file-digest path "md5" true))}
    "sha1" {:type "sha1"
            :value (str "{sha1}" (file-digest path "sha1" false))}
    "sha1lite" {:type "sha1lite"
                :value (str "{sha1lite}" (file-digest path "sha1" true))}
    "sha256" {:type "sha256"
           :value (str "{sha256}" (file-digest path "sha256" false))}
    "sha256lite" {:type "sha256lite"
               :value (str "{sha256lite}" (file-digest path "sha256" true))}))

(defn file-metadata
  ([path]
   (file-metadata path "md5" false))
  ([path checksum-type follow-links?]
    (let [attributes (file-attributes path follow-links?)
          follow (if follow-links? "follow" "manage")
          base-attributes {:path path
                           :relative_path nil
                           :destination nil
                           :owner (get attributes "uid")
                           :group (get attributes "gid")
                           :mode (attributes->mode attributes)
                           :links follow}]
      (case (attributes->type attributes)

        :file (assoc base-attributes
                      :type "file"
                      :checksum (file-checksum path attributes checksum-type))
        :directory (assoc base-attributes
                           :type "directory"
                           :checksum (file-checksum path attributes "ctime"))
        :link (assoc base-attributes
                      :type "link"
                      :destination (if follow-links?
                                     nil
                                     (readlink path))
                      :checksum (file-checksum path attributes checksum-type))))))

(defn find-in-modulepath
  "Walks a modulepath and returns a path to the first existing file entry
  or nil if no existing file is found."
  [modulepath infix path]
  (some
    #(let [test-file (->> (clojure.string/replace-first path #"^/" "")
                          (io/file % infix))]
       (if (.exists test-file) (.toString test-file)))
    modulepath))

(defn subdirs
  "Return all subdirectories of a path."
  [path]
  (->> path
       io/file
       .listFiles
       (filter #(.isDirectory %))
       (map #(.toString %))))

;; TODO: Way too much stuff going on here. Fix later.
(defn walk-file-tree
  [path checksum-type follow-links?]
  (let [root (-> path io/as-file .toPath)
        results (transient [])
        visit-fn (fn [file attrs]
                   (let [metadata (file-metadata (.toString file) checksum-type follow-links?)
                          rel-path (->> file
                                        (.relativize root)
                                        .toString)]
                      (conj! results (assoc metadata
                                            :path path
                                            :relative_path (case rel-path
                                                             "" "."
                                                             rel-path))))
                    FileVisitResult/CONTINUE)
        visitor (proxy [SimpleFileVisitor] []
                  (preVisitDirectory [file attrs]
                    (visit-fn file attrs))
                  (visitFile [file attrs]
                    (visit-fn file attrs)))]

    (if follow-links?
      (Files/walkFileTree root #{FileVisitOption/FOLLOW_LINKS} Integer/MAX_VALUE visitor)
      (Files/walkFileTree root #{} Integer/MAX_VALUE visitor))

    (persistent! results)))


;; File Content API

(defn file-response
  [file]
  (let [response (response/file-response
                   file
                   {:allow-symlinks? true :index-files? false :root false})]
    (assoc-in response [:headers "Content-Type"] "application/octet-stream")))

(defn module-file-handler
  [context]
  (fn [request]
    ;; FIXME: Lots of stuff not handled below. Ensure environment is present as
    ;; a query parameter. Ensure the requested environment is present in our
    ;; context. Ensure we only return a file, or the content of a symlink but
    ;; not directories or other special file types.
    (let [environment (get-in request [:params "environment"])
          module (get-in request [:route-params :module])
          path (get-in request [:route-params :path])
          modulepath (get-in @(:environments context) [environment "modulepath"])
          file (find-in-modulepath modulepath (str module "/files") path)]
      (if file
        (file-response file)
        (let [msg (str "Not Found: Could not find file_content modules/" (str module path))]
          (request-utils/json-response
            404
            {:message msg :issue_kind "RESOURCE_NOT_FOUND"}))))))

(defn module-plugin-handler
  [context plugin-path]
  (fn [request]
    (let [environment (get-in request [:params "environment"])
          path (get-in request [:route-params :path])
          modulepath (get-in @(:environments context) [environment "modulepath"])
          moduledirs (mapcat subdirs modulepath)
          file (find-in-modulepath moduledirs plugin-path path)]
      (if file
        (file-response file)
        (let [msg (str "Not Found: Could not find plugin " plugin-path "/" path)]
          (request-utils/json-response
            404
            {:message msg :issue_kind "RESOURCE_NOT_FOUND"}))))))

(defn file-content-handler
  [context]
  (let [module-handler (module-file-handler context)
        plugin-handler (module-plugin-handler context "lib")
        pluginfact-handler (module-plugin-handler context "facts.d")]
    (-> (comidi/routes
          (comidi/context "/puppet/v3/file_content"
            (comidi/GET ["/modules/" [#"[a-z][a-z0-9_]*" :module] [#".*" :path]] request
                        (module-handler request))
            (comidi/GET ["/plugins/" [#".*" :path]] request
                        (plugin-handler request))
            (comidi/GET ["/pluginfacts/" [#".*" :path]] request
                        (pluginfact-handler request))))

        comidi/routes->handler
        params/wrap-params)))


;; File Metadata API

(defn module-metadata-handler
  [context]
  (fn [request]
    ;; FIXME: Lots of stuff not handled below. Ensure environment is present as
    ;; a query parameter. Ensure the requested environment is present in our
    ;; context. Ensure we only return a file, or the content of a symlink but
    ;; not directories or other special file types.
    (let [environment (get-in request [:params "environment"])
          module (get-in request [:route-params :module])
          path (get-in request [:route-params :path])
          modulepath (get-in @(:environments context) [environment "modulepath"])
          file (find-in-modulepath modulepath (str module "/files") path)
          ;; FIXME: Only allowed values are "manage" and "follow". There's
          ;; also a "source_permissions" parameter documented, but that
          ;; seems to be a docs bug since the param isn't actually used
          ;; in the API code.
          follow-links? (case (get-in request [:params "links"] "manage")
                          "manage" false
                          true)
          checksum-type (get-in request [:params "checksum_type"] "md5")]
      (if file
        (response/content-type
          (request-utils/json-response 200 (file-metadata file checksum-type follow-links?))
          "text/pson")
        (let [msg (str "Not Found: Could not find file_metadata modules/" (str module path))]
          (request-utils/json-response
            404
            {:message msg :issue_kind "RESOURCE_NOT_FOUND"}))))))

(defn plugin-metadata-handler
  [context plugin-path]
  (fn [request]
    (let [environment (get-in request [:params "environment"])
          path (get-in request [:route-params :path])
          modulepath (get-in @(:environments context) [environment "modulepath"])
          moduledirs (mapcat subdirs modulepath)
          file (find-in-modulepath moduledirs plugin-path path)
          ;; FIXME: Only allowed values are "manage" and "follow". There's
          ;; also a "source_permissions" parameter documented, but that
          ;; seems to be a docs bug since the param isn't actually used
          ;; in the API code.
          follow-links? (case (get-in request [:params "links"] "manage")
                          "manage" false
                          true)
          checksum-type (get-in request [:params "checksum_type"] "md5")]
      (if file
        (response/content-type
          (request-utils/json-response 200 (file-metadata file checksum-type follow-links?))
          "text/pson")
        (let [msg (str "not found: could not find plugin " plugin-path "/" path)]
          (request-utils/json-response
            404
            {:message msg :issue_kind "resource_not_found"}))))))

(defn file-metadata-handler
  [context]
  (let [module-handler (module-metadata-handler context)
        plugin-handler (plugin-metadata-handler context "lib")
        pluginfact-handler (plugin-metadata-handler context "facts.d")]
    (-> (comidi/routes
          (comidi/context "/puppet/v3/file_metadata"
            (comidi/GET ["/modules/" [#"[a-z][a-z0-9_]*" :module] [#".*" :path]] request
                        (module-handler request))
            (comidi/GET ["/plugins/" [#".*" :path]] request
                        (plugin-handler request))
            (comidi/GET ["/pluginfacts/" [#".*" :path]] request
                        (pluginfact-handler request))))

        comidi/routes->handler
        params/wrap-params)))


;; File Metadatas API

(defn module-metadatas-handler
  [context]
  (fn [request]
    ;; FIXME: Lots of stuff not handled below. Ensure environment is present as
    ;; a query parameter. Ensure the requested environment is present in our
    ;; context. Ensure we only return a file, or the content of a symlink but
    ;; not directories or other special file types.
    (let [environment (get-in request [:params "environment"])
          module (get-in request [:route-params :module])
          path (get-in request [:route-params :path])
          modulepath (get-in @(:environments context) [environment "modulepath"])
          root (str module "/files")
          ;; FIXME: Only allowed values are "manage" and "follow". There's
          ;; also a "source_permissions" parameter documented, but that
          ;; seems to be a docs bug since the param isn't actually used
          ;; in the API code.
          follow-links? (case (get-in request [:params "links"] "manage")
                          "manage" false
                          true)
          checksum-type (get-in request [:params "checksum_type"] "md5")]
      (response/content-type
        (request-utils/json-response 200 (walk-file-tree root checksum-type follow-links?))
        "text/pson"))))

(defn plugin-metadatas-handler
  [context plugin-path]
  (fn [request]
    (let [environment (get-in request [:params "environment"])
          path (get-in request [:route-params :path])
          modulepath (get-in @(:environments context) [environment "modulepath"])
          moduledirs (->> (mapcat subdirs modulepath)
                          (map #(str % "/" plugin-path))
                          (filter #(-> % io/as-file .exists)))
          ;; FIXME: Only allowed values are "manage" and "follow". There's
          ;; also a "source_permissions" parameter documented, but that
          ;; seems to be a docs bug since the param isn't actually used
          ;; in the API code.
          follow-links? (case (get-in request [:params "links"] "manage")
                          "manage" false
                          true)
          checksum-type (get-in request [:params "checksum_type"] "md5")
          metadata (if (empty? moduledirs)
                     (as-> modulepath p
                          (filter #(-> % io/as-file .exists) p)
                          (first p)
                          (file-metadata p)
                          (assoc p :relative_path ".")
                          (conj [] p))
                     (mapcat
                         #(walk-file-tree % checksum-type follow-links?)
                         moduledirs))]
      (response/content-type
        (request-utils/json-response 200 metadata)
        "text/pson"))))

(defn file-metadatas-handler
  [context]
  (let [module-handler (module-metadata-handler context)
        plugin-handler (plugin-metadatas-handler context "lib")
        pluginfact-handler (plugin-metadatas-handler context "facts.d")]
    (-> (comidi/routes
          (comidi/context "/puppet/v3/file_metadatas"
            (comidi/GET ["/modules" [#"[a-z][a-z0-9_]*" :module] [#".*" :path]] request
              (module-handler request))
            (comidi/GET ["/plugins" [#".*" :path]] request
                        (plugin-handler request))
            (comidi/GET ["/pluginfacts" [#".*" :path]] request
                        (pluginfact-handler request))))

        comidi/routes->handler
        params/wrap-params)))
