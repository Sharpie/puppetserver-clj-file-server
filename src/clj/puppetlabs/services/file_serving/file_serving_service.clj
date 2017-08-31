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
  [[:WebserverService add-ring-handler]
   [:JRubyPuppetService]]
  (init [this context]
    (log/info "Initializing FileServing service")
    (let [context (assoc context
                         :environments (atom {})
                         :ruby-mounts (atom [])
                         :jruby-service (services/get-service this :JRubyPuppetService))]

      ;; Mounts a router that responds to /file-serving:
      ;;
      ;;   GET    -> Return currently registered environments and mount points.
      ;;   DELETE -> Update environments and mount points.
      (add-ring-handler (core/admin-handler context) "/")
      (add-ring-handler (core/file-content-handler context) "/puppet/v3/file_content")
      (add-ring-handler (core/file-metadata-handler context) "/puppet/v3/file_metadata")
      (add-ring-handler (core/file-metadatas-handler context) "/puppet/v3/file_metadatas")

      context))

  (start [this context]
    (log/info "Starting FileServing service")

    ;; Sync state of environments and mountpoints now that dependent services
    ;; have started.
    (core/refresh-jruby-state! context)

    (log/info (with-out-str
                (clojure.pprint/pprint @(:environments context))))

    (log/info (with-out-str
                (clojure.pprint/pprint @(:ruby-mounts context))))

    context)
  (stop [this context]
    (log/info "Shutting down FileServing service")
    context)
  (hello [this caller]
         (core/hello caller)))
