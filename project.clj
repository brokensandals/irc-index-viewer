(defproject irc-index-viewer "1.0.0-SNAPSHOT"
  :description "Webapp for viewing IRC logs stored in an elasticsearch index."
  :dependencies [[cheshire "2.0.2"]
                 [clj-http "0.2.3"]
                 [clj-time "0.3.1"]
                 [enlive "1.0.0"]
                 [hiccup "0.3.7"]
                 [net.cgrand/moustache "1.0.0"]
                 [org.clojure/clojure "1.3.0"]
                 [org.clojure/tools.logging "0.2.3"]
                 [ring/ring-core "1.0.0-RC1"]]
  :dev-dependencies [[lein-ring "0.4.6"]]
  :ring {:handler irc-index-viewer.core/handler
         :init irc-index-viewer.config/find-and-load-configuration})