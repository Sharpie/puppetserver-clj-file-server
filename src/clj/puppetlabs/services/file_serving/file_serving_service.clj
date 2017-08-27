(ns puppetlabs.services.file-serving.file-serving-service
  (:require
   [clojure.tools.logging :as log]
   [puppetlabs.bodgery.jruby :as jruby]
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
    context)
  (start [this context]
    (log/info "Starting FileServing service")
    (log/info (with-out-str
                (clojure.pprint/pprint
                  (jruby/run-script! this "file_serving_shims/get_environments.rb"))))

    (log/info (with-out-str
                (clojure.pprint/pprint
                  (jruby/run-script! this "file_serving_shims/get_mounts.rb"))))
    context)
  (stop [this context]
    (log/info "Shutting down FileServing service")
    context)
  (hello [this caller]
         (core/hello caller)))
