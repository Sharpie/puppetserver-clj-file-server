(defproject puppetlabs.services/file-serving "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :pedantic? :abort

  :min-lein-version "2.7.1"

  :plugins [[lein-parent "0.3.1"]]

  :parent-project {:coords [puppetlabs/clj-parent "1.3.2"]
                   :inherit [:managed-dependencies]}

  :source-paths ["src/clj"]

  :dependencies [[org.clojure/clojure]

                 [puppetlabs/trapperkeeper]
                 [puppetlabs/trapperkeeper-webserver-jetty9]]

  :profiles {:dev {:source-paths ["dev"]
                   :repl-options {:init-ns tk-devtools}
                   :dependencies [[org.clojure/tools.namespace]

                                  ;; Re-declare dependencies with "test"
                                  ;; classifiers to pull in additional testing
                                  ;; code, helper functions and libraries.
                                  [puppetlabs/trapperkeeper-webserver-jetty9 nil :classifier "test"]
                                  [puppetlabs/trapperkeeper nil :classifier "test" :scope "test"]
                                  [puppetlabs/kitchensink nil :classifier "test" :scope "test"]]}}

  :aliases {"tk" ["trampoline" "run" "--config" "dev-resources/config.conf"]}

  :main puppetlabs.trapperkeeper.main)
