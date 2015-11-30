(defproject semira "1.0.0-SNAPSHOT"
  :description "Semira sings songs."
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.7.228"]
                 [compojure "1.4.0"]
                 [ring/ring-jetty-adapter "1.4.0"]
                 [hiccup "1.0.5"]
                 [ring-partial-content "1.0.0"]
                 [org/jaudiotagger "2.0.3"]
                 [org.clojure/core.async "0.2.374"]
                 [cljs-http "0.1.39"]
                 [reagent "0.5.1"]]

  :plugins [[lein-cljsbuild "1.1.2"]
            [lein-figwheel "0.5.0-6"]]

  :figwheel {:css-dirs ["resources/public"]}

  :profiles {:dev {:dependencies [[figwheel-sidecar "0.5.0-6"]
                                  [com.cemerick/piggieback "0.2.1"]]}
             :uberjar {:aot :all
                       :hooks [leiningen.cljsbuild]}}
  :main semira.core
  :uberjar-name "semira.jar"

  :cljsbuild {:builds {:prod {:source-paths ["src"]
                              :compiler {:optimizations :advanced
                                         :output-to "resources/public/semira.js"
                                         :output-dir "resources/public/semira"}}
                       :dev {:source-paths ["dev" "src"]
                             :figwheel true
                             :compiler {:output-to "resources/public/semira-dev.js"
                                        :output-dir "resources/public/semira-dev"}}}}

  :repl-options {:init (do (use 'figwheel-sidecar.repl-api) (start-figwheel!))
                 :init-ns semira.core})
