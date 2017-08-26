(ns puppetlabs.services.file-serving.file-serving-service
  (:require [clojure.tools.logging :as log]
            [puppetlabs.services.file-serving.file-serving-core :as core]
            [puppetlabs.trapperkeeper.core :refer [defservice]]))

(defprotocol FileServingService
  (hello [this caller]))

(defservice file-serving-service
  FileServingService
  [[:WebroutingService add-ring-handler]]
  (init [this context]
    (log/info "Initializing FileServing service")
    (add-ring-handler this (fn [request] {:status 200
                                          :headers {"Content-Type" "text/plain"}
                                          :body "hello, world!"}))
    context)
  (start [this context]
    (log/info "Starting FileServing service")
    context)
  (stop [this context]
    (log/info "Shutting down FileServing service")
    context)
  (hello [this caller]
         (core/hello caller)))
