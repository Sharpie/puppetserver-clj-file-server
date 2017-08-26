(ns puppetlabs.services.file-serving-service-test
  (:require [clojure.test :refer :all]
            [puppetlabs.trapperkeeper.app :as app]
            [puppetlabs.trapperkeeper.testutils.bootstrap :refer [with-app-with-config]]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-service :refer [jetty9-service]]
            [puppetlabs.trapperkeeper.services.webrouting.webrouting-service :refer [webrouting-service]]
            [puppetlabs.services.file-serving.file-serving-service :as svc]))

(deftest file-serving-service-test
  (testing "says hello to caller"
    (with-app-with-config
      app
      [jetty9-service
       webrouting-service
       svc/file-serving-service]
      {:webserver {:host "localhost"
                   :port 8090}
       :web-router-service {:puppetlabs.services.file-serving.file-serving-service/file-serving-service "/file_content"}}

      (let [service (app/get-service app :FileServingService)]
        (is (= "Hello, foo!" (svc/hello service "foo")))))))
