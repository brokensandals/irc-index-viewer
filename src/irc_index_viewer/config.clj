(ns irc-index-viewer.config
  (:require [clj-time.core :as time]))

(def ^:dynamic
     ^{:doc "URL to elasticsearch index."}
  *index*)

(def ^:dynamic
     ^{:doc "Number of transcript documents to retrieve from the index for a 'most recent' or search results page."}
  *transcripts-per-page*)

(def ^:dynamic
     ^{:doc "On 'most recent' pages, transcripts from a channel will be split/combined such that there is this duration
             (specified in minutes in the configuration) of inactivity between them."}
  *transcript-split-threshold*)

(def ^{:doc "The current app-wide configuration."}
  configuration (ref {}))

(def ^:private
  default-configuration
    {:index "http://localhost:9200/irc"
     :transcripts-per-page 15
     :transcript-split-threshold 20})

(defn find-and-load-configuration
  "Looks for configuration at a path specified by the system property
   irc-index-viewer.configfile, or if that isn't specified, uses default-configuration."
  []
  (dosync
    (ref-set configuration
      (if-let [config-path (System/getProperty "irc-index-viewer.configfile")]
              (merge default-configuration (read-string (slurp config-path)))
              default-configuration))))

(defmacro with-current-configuration
  "Bind the dynamic configuration vars using the current app-wide configuration."
  [& body]
  `(binding [*index* (:index @configuration)
             *transcripts-per-page* (:transcripts-per-page @configuration)
             *transcript-split-threshold* (time/minutes (:transcript-split-threshold @configuration))]
     ~@body))

(defn wrap-current-configuration
  "Wrap handler with with-current-configuration."
  [handler]
  #(with-current-configuration (handler %)))