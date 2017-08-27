(ns puppetlabs.services.file-serving.file-serving-service
  (:require
   [clojure.tools.logging :as log]
   [puppetlabs.services.file-serving.file-serving-core :as core]
   [puppetlabs.trapperkeeper.core :refer [defservice]]
   [puppetlabs.trapperkeeper.services :as services]
   [puppetlabs.trapperkeeper.services.webserver.jetty9-core :as jetty-core]))


(defprotocol FileServingService
  (hello [this caller]))

(defservice file-serving-service
  FileServingService
  [[:WebroutingService]]
  (init [this context]
    (log/info "Initializing FileServing service")
    ;; This let sets up an ugly hack that punches through the WebserverService
    ;; and pulls out the guts of it's state so that we can call the private
    ;; add-handler function defined in the Jetty core. This is required because
    ;; trapperkeeper-jetty doesn't expose methods for adding arbitrary handlers
    ;; to a server.
    (let [jetty (services/get-service this :WebserverService)
          server-context (-> jetty
                             services/service-context
                             :jetty9-servers
                             :default)]

      (jetty-core/add-handler server-context
                              (core/build-mock-file-handler "/file_content"
                                                            ["/tmp/foo" "/tmp/bar"])
                              false))
    context)
  (start [this context]
    (log/info "Starting FileServing service")
    context)
  (stop [this context]
    (log/info "Shutting down FileServing service")
    context)
  (hello [this caller]
         (core/hello caller)))
