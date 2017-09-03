(ns puppetlabs.services.file-serving.file-serving-service
  (:require
   [clojure.tools.logging :as log]
   [puppetlabs.services.file-serving.file-serving-core :as core]
   [puppetlabs.trapperkeeper.core :refer [defservice]]
   [puppetlabs.trapperkeeper.services :as services]
   [puppetlabs.trapperkeeper.services.status.status-core :as status-core]))


(defprotocol FileServingService
  (handle-request
    [this request]))

(defservice clj-file-serving-service
  FileServingService
  [[:StatusService register-status]
   [:WebserverService add-ring-handler]
   [:JRubyPuppetService]]
  (init [this context]
    (log/info "Initializing FileServing service")
    (let [context (assoc context
                         :environments (atom {})
                         :ruby-mounts (atom [])
                         :jruby-service (services/get-service this :JRubyPuppetService))
          handler (core/build-request-handler context)]

      (add-ring-handler handler "/puppet/v3/file_content")
      (add-ring-handler handler "/puppet/v3/file_metadata")
      (add-ring-handler handler "/puppet/v3/file_metadatas")

      (register-status "clj-file-server"
                       (status-core/get-artifact-version "puppetserver" "clj-file-server")
                       1
                       (core/build-status-callback context))

      (assoc context :request-handler handler)))

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

  (handle-request
    [this request]
    (if-let [handler (:request-handler (services/service-context this))]
      (handler request))))
