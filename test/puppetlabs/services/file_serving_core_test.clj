(ns puppetlabs.services.file-serving-core-test
  (:require [clojure.test :refer :all]
            [puppetlabs.services.file-serving.file-serving-core :refer :all]))

(deftest hello-test
  (testing "says hello to caller"
    (is (= "Hello, foo!" (hello "foo")))))
