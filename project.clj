(defproject routes "0.4.4"
  :description "URL-driven routes handling"
  :url "https://github.com/chbrown/routes-clojure"
  :license {:name "Eclipse Public License"
            :url "https://www.eclipse.org/legal/epl-v10.html"}
  :deploy-repositories [["releases" :clojars]]
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.946"]]
  :plugins [[lein-cljsbuild "1.1.7"]]
  :cljsbuild {:builds [{:id "production"
                        :source-paths ["src"]
                        :compiler {:output-dir "target"
                                   :output-to "target/main.js"
                                   :optimizations :simple
                                   :pretty-print false}}
                       {:id "test"
                        :source-paths ["src" "test"]
                        :compiler {:output-dir "target/test"
                                   :output-to "target/test/main.js"
                                   :main routes.runner
                                   :optimizations :whitespace}}]}
  :profiles {:test {:doo {:paths {:rhino "lein run -m org.mozilla.javascript.tools.shell.Main"}}
                    :plugins [[lein-doo "0.1.8"]
                              [lein-cloverage "1.0.10"]]}
             :dev {:dependencies [[org.clojure/tools.namespace "0.3.0-alpha3"]
                                  [org.clojure/tools.trace "0.7.9"]]
                   :source-paths ["dev"]
                   :repl-options {:init-ns user}}})
