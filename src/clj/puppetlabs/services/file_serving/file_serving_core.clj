(ns puppetlabs.services.file-serving.file-serving-core
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
