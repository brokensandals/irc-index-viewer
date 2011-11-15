(ns irc-index-viewer.path
  (:require [clj-time.core :as time]
            [clj-time.format :as time-format]
            [clojure.string :as string])
  (:use [hiccup.page-helpers :only [url]]
        ring.util.codec))

(def time-path-formatter (time-format/formatter "'t'HH:mm:ss"))

(defn path-to
  "Encode & join a set of URL path segments."
  [& components]
  (url
    (string/join
      (map #(str "/" (url-encode %)) components))))

(defn server-path
  "Path to the 'recent activity' page for given server."
  [server]
  (path-to server))

(defn- trim-channel
  "Remove leading hash from channel name - it will be
   added back in the controller, and omitting it gives a prettier URL."
  [channel]
  (if (.startsWith channel "#")
      (.substring channel 1)
      channel))

(defn channel-path
  "Path to the 'recent activity' page for given server & channel."
  [server channel]
  (path-to server (trim-channel channel)))

(defn date-path
  "Path to the 'display' page for given date."
  [server channel log-time]
  (path-to server
           (trim-channel channel)
           (str (time/year log-time))
           (str (time/month log-time))
           (str (time/day log-time))))

(defn time-path
  "Path to the 'display' page for given date, with fragment specifier for given time."
  [server channel log-time]
  (str (date-path server channel log-time)
       "#" (time-format/unparse time-path-formatter log-time)))