(ns puppetlabs.services.file-serving.file-serving-core
  (:require
   [clojure.java.io :as io]
   [puppetlabs.comidi :as comidi]
   [puppetlabs.ring-middleware.utils :as request-utils]
   [puppetlabs.bodgery.jruby :as jruby]
   [ring.middleware.params :as params]
   [ring.util.response :as response])
  (:import
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
(defn find-in-modulepath
  "Walks a modulepath and returns a path to the first existing file entry
  or nil if no existing file is found."
  [modulepath infix path]
  (some
    #(let [test-file (->> (clojure.string/replace-first path #"^/" "")
                          (io/file % infix))]
       (if (.exists test-file) (.toString test-file)))
    modulepath))

(defn file-response
  [file]
  (let [response (response/file-response
                   file
                   {:allow-symlinks? true :index-files? false :root false})]
    (assoc-in response [:headers "Content-Type"] "application/octet-stream")))

(defn subdirs
  "Return all subdirectories of a path."
  [path]
  (->> path
       io/file
       .listFiles
       (filter #(.isDirectory %))
       (map #(.toString %))))

;; File Content API

(defn module-file-handler
  [context]
  (fn [request]
    ;; FIXME: Lots of stuff not handled below. Ensure environment is present as
    ;; a query parameter. Ensure the requested environment is present in our
    ;; context.
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
