(ns puppetlabs.services.file-serving.file-serving-core
  (:require
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

(defn refresh-jruby-state!
  "Refreshes the state of environments and mount points visible to the Ruby
  layer."
  [context]
  (let [jruby-service (:jruby-service context)]
    (reset! (:environments context)
            (jruby/run-script! jruby-service "file_serving_shims/get_environments.rb"))
    (reset! (:ruby-mounts context)
            (jruby/run-script! jruby-service "file_serving_shims/get_mounts.rb"))))
