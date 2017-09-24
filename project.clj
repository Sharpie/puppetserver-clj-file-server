(def puppetserver-version
  "Version of Puppet Server to develop and test against"
  "5.0.0")

(defproject puppetserver/clj-file-server "0.2.0"
  :description "Prototype file server service for Puppet Server"
  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0.html"}

  :pedantic? :abort

  :min-lein-version "2.7.1"

  :plugins [[lein-parent "0.3.1"]]

  :parent-project {:coords [puppetlabs/clj-parent "1.2.1"]
                   :inherit [:managed-dependencies]}

  :source-paths ["src/clj"]
  :resource-paths ["src/ruby"]
  :test-paths ["test/integration"]

  :dependencies [[org.clojure/clojure]

                 [commons-codec]
                 [commons-io]

                 [ring/ring-core]

                 [prismatic/plumbing]

                 [puppetlabs/comidi]
                 [puppetlabs/ring-middleware]

                 [puppetlabs/trapperkeeper]
                 [puppetlabs/trapperkeeper-status]
                 [puppetlabs/trapperkeeper-webserver-jetty9]

                 [puppetlabs/jruby-utils "0.10.0"]]

  :profiles {:dev {:source-paths ["dev"]
                   :repl-options {:init-ns tk-devtools}
                   :resource-paths ["dev-resources"]
                   :dependencies [[org.clojure/tools.namespace]
                                  [org.clojure/tools.nrepl]

                                  [cheshire]
                                  [ring-mock]

                                  ;; Re-declare dependencies with "test"
                                  ;; classifiers to pull in additional testing
                                  ;; code, helper functions and libraries.
                                  [puppetlabs/trapperkeeper-webserver-jetty9 nil :classifier "test"]
                                  [puppetlabs/trapperkeeper nil :classifier "test" :scope "test"]
                                  [puppetlabs/kitchensink nil :classifier "test" :scope "test"]

                                  [puppetlabs/puppetserver ~puppetserver-version :classifier "test" :scope "test"]]}

             :module {:jar-name "clj-file-server.jar"}}

  :aliases {"tk" ["trampoline" "run" "--config" "dev-resources/config.conf"]}

  :main puppetlabs.trapperkeeper.main)
