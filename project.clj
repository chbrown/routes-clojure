(defproject routes "0.1.1-SNAPSHOT"
  :description "URL-driven routes handling"
  :url "https://github.com/chbrown/routes-clojure"
  :license {:name "Eclipse Public License"
            :url "https://www.eclipse.org/legal/epl-v10.html"}
  :deploy-repositories [["releases" :clojars]]
  :plugins [[lein-cljsbuild "1.1.7"]]
  :cljsbuild {:builds [{:source-paths ["src"]
                        :compiler {:optimizations :simple
                                   :pretty-print false}}]}
  :profiles {:test {:plugins [[lein-cloverage "1.0.9"]]}
             :dev {:dependencies [[org.clojure/clojure "1.8.0"]
                                  [org.clojure/clojurescript "1.9.908"]
                                  [org.clojure/tools.namespace "0.3.0-alpha3"]
                                  [org.clojure/tools.trace "0.7.9"]]
                   :source-paths ["dev"]
                   :repl-options {:init-ns user}}})
