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
  [[:WebroutingService]
   [:JRubyPuppetService]]
  (init [this context]
    (log/info "Initializing FileServing service")
    (assoc context
           :environments (atom {})
           :ruby-mounts (atom [])
           :jruby-service (services/get-service this :JRubyPuppetService)))
  (start [this context]
    (log/info "Starting FileServing service")
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
