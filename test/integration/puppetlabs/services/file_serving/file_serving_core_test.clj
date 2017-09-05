(ns puppetlabs.services.file-serving.file-serving-core-test
  (:require
    [clojure.java.io :as io]
    [clojure.test :refer :all]

    [cheshire.core :as json]

    [puppetlabs.services.protocols.request-handler :as jruby-file-server]
    [puppetlabs.services.file-serving.file-serving-service :as clj-file-server]

    [puppetlabs.trapperkeeper.app :as tk-app]
    [puppetlabs.trapperkeeper.bootstrap :as tk-bootstrap]
    [puppetlabs.trapperkeeper.config :as tk-config]
    [puppetlabs.trapperkeeper.testutils.bootstrap :as tst-bootstrap]

    [ring.mock.request :as ring-mock]))


(def bootstrap-config
  (-> "clj-file-server/bootstrap.cfg"
      io/resource
      .getPath))

(def app-config
  (-> "clj-file-server/config.conf"
      io/resource
      .getPath))

(def logback-config
  (-> "clj-file-server/logback-test.xml"
      io/resource
      .getPath))

(def codedir
  (-> "clj-file-server/fixtures/codedir"
      io/resource
      .getPath))

(def app-services
  (tk-bootstrap/parse-bootstrap-config! bootstrap-config))

(def base-config
  "Load dev config, but shift to a different port and turn logging down."
  (-> app-config
      tk-config/load-config
      (assoc-in [:webserver :ssl-port] 18140)
      (assoc-in [:global :logging-config] logback-config)
      (assoc-in [:jruby-puppet :master-code-dir] codedir)))

(defn get-jruby-handler
  [app]
  (partial jruby-file-server/handle-request
           (tk-app/get-service app :RequestHandlerService)))

(defn get-clj-handler
  [app]
  (partial clj-file-server/handle-request
           (tk-app/get-service app :FileServingService)))

(defn with-json-body
  "Parses a JSON response body back into a map for better diffing."
  [response]
  (assoc response :body (-> response :body json/parse-string)))

(defn scrub-headers
  "Remove headers that are expected to be present in Clojure responses."
  [response]
  (update-in response [:headers] dissoc "X-Puppetserver-Service"))


;; Test responses generated by the Clojure implementation against those
;; generated by the Ruby implementation and report any regressions.
(deftest jruby-regression-tests
  (tst-bootstrap/with-app-with-config
    app
    app-services
    base-config
    (let [ruby-handler (get-jruby-handler app)
          clj-handler (get-clj-handler app)]

      (testing "/file_metadatas API"
        (let [req (ring-mock/request
                    :get
                    "/puppet/v3/file_metadatas/plugins"
                    {"environment" "production"
                     "recurse" "true"})
              request (ring-mock/header req "Accept" "text/pson")
              ruby-response (with-json-body (ruby-handler request))
              clj-response (-> (clj-handler request)
                               with-json-body
                               scrub-headers)]
          (is (= ruby-response clj-response)))))))