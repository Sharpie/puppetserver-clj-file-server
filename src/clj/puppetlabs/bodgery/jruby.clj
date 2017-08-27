(ns puppetlabs.bodgery.jruby
  "Utility functions for interacting with JRuby instances inside of a
  Puppet Server. These functions are useful for prototyping work, but
  any serious use should be re-built as part of the main JRubyPuppet
  interface."
  (:require
    [clojure.java.io :as io]
    [puppetlabs.trapperkeeper.services :as tk]
    [puppetlabs.services.jruby-pool-manager.jruby-core :as jruby-core]))

(defn jruby-pool
  "Retrieves a reference to the JRuby pook using any other instance of a
  TrapperKeeper service. Nasty action at a distance."
  [tk-service]
  (-> tk-service
      (tk/get-service :JRubyPuppetService)
      tk/service-context
      :pool-context))

(defmacro with-jruby
  "A macro that handles borrowing a JRuby for the duration of an operation
  and then returning it to the pool. The JRuby instance is available as
  jruby-varname during the operation."
  [tk-service jruby-varname & body]
  `(let [pool-context# (jruby-pool ~tk-service)]
     (jruby-core/with-jruby-instance
       jruby-instance#
       pool-context#
       "puppetlabs.spike.jruby-utils ruby operation"
       (let [~jruby-varname (:scripting-container jruby-instance#)]
         ~@body))))

(defn ruby-script-input
  "Retrieve a Ruby script from the Java resource path and create an InputStream
  for reading it."
  [script-path]
  (-> script-path
      io/resource
      io/input-stream))

(defn run-script!
  "Run a ruby script and return its output."
  [tk-service script-path]
  (with-jruby tk-service jruby
    (.runScriptlet
      jruby
      (ruby-script-input script-path)
      script-path)))
