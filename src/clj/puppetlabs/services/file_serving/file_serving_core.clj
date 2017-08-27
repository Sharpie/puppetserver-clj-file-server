(ns puppetlabs.services.file-serving.file-serving-core
  (:require
   [puppetlabs.comidi :as comidi]
   [puppetlabs.ring-middleware.utils :as request-utils]
   [puppetlabs.bodgery.jruby :as jruby])
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
