(defproject puppetserver/clj-file-server "0.1.0-SNAPSHOT"
  :description "Prototype file server service for Puppet Server"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :pedantic? :abort

  :min-lein-version "2.7.1"

  :plugins [[lein-parent "0.3.1"]]

  :parent-project {:coords [puppetlabs/clj-parent "1.3.2"]
                   :inherit [:managed-dependencies]}

  :source-paths ["src/clj"]
  :resource-paths ["src/ruby"]

  :dependencies [[org.clojure/clojure]

                 [commons-codec]
                 [commons-io]

                 [ring/ring-core]

                 [puppetlabs/comidi]
                 [puppetlabs/ring-middleware]

                 [puppetlabs/trapperkeeper]
                 [puppetlabs/trapperkeeper-status]
                 [puppetlabs/trapperkeeper-webserver-jetty9]

                 [puppetlabs/jruby-utils "0.10.0"]]

  :profiles {:dev {:source-paths ["dev"]
                   :repl-options {:init-ns tk-devtools}
                   :dependencies [[org.clojure/tools.namespace]

                                  ;; Re-declare dependencies with "test"
                                  ;; classifiers to pull in additional testing
                                  ;; code, helper functions and libraries.
                                  [puppetlabs/trapperkeeper-webserver-jetty9 nil :classifier "test"]
                                  [puppetlabs/trapperkeeper nil :classifier "test" :scope "test"]
                                  [puppetlabs/kitchensink nil :classifier "test" :scope "test"]]}

             :module {:jar-name "clj-file-server.jar"}}

  :aliases {"tk" ["trampoline" "run" "--config" "dev-resources/config.conf"]}

  :main puppetlabs.trapperkeeper.main)
