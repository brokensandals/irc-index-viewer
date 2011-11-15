(ns irc-index-viewer.controller
  (:require [clj-time.core :as time])
  (:use irc-index-viewer.config
        irc-index-viewer.search
        irc-index-viewer.views
        ring.util.response))

(defn- html
  "Adds a content-type of text/html to a response."
  ;FIXME: the 'doall' is a quick hack to ensure that the *base-url*
  ;       var is still set when the template is evaluated.
  [content]
  (content-type (response (doall content)) "text/html"))

(defn- normalize-channel
  "Add a leading pound to the channel name if absent."
  ; The data is stored including the prefix so if I wanted to support
  ; channels with other prefixes, it would just require
  ; changing this function to not modify channel names beginning with them.
  ; ...but I can't imagine that every happening
  [channel]
  (when channel
    (if (.startsWith channel "#")
        channel
        (str "#" channel))))

; TODO: for the 'most recent' page I only want this to combine
; transcripts within a certain time range of each other
; To go along with that it'd be nice to split a transcript with
; containing a large empty time period into multiple transcripts
(defn- combine-adjacent-transcripts
  "For simple newest/oldest searches, if we have two transcripts
   from the same channel right next to each other, we know there
   are no intervening transcripts, so we can reduce clutter by
   combining them."
  [transcripts newest-first]
  (for [group (partition-by
                (fn [{:keys [server channel]}] [server channel])
                transcripts)]
    (assoc (first group) :entries
      (apply concat (map :entries
                         (if newest-first (reverse group) group))))))

(defn recent-activity-handler
  "Searches for the newest transcripts, optionally restricted by
   server or server+channel."
  [req [server raw-channel]]
  (let [channel (normalize-channel raw-channel)
        result (search :server server
                       :channel channel
                       :from 0
                       :size *transcripts-per-page*
                       :sort :new)
        transcripts (combine-adjacent-transcripts (:transcripts result) true)]
    (html (layout (results-section :transcripts transcripts)
                  :title (recent-activity-title server channel)
                  :servers-channels (servers-channels)
                  :current-server server
                  :current-channel channel))))

(defn display-handler
  "Views transcripts for a particular day."
  [req server raw-channel year month day]
  (let [channel (normalize-channel raw-channel)
        start-date (time/date-time year month day)
        range (time/interval start-date (time/plus start-date (time/days 1)))
        result (search :server server
                       :channel channel
                       :sort :old
                       :from 0
                       :size 1000000 ;TODO there should never be anywhere near this many matches,
                                     ;but I don't know if there are adverse effects of merely setting
                                     ;such a high size
                       :log-time-range range)
        transcripts (combine-adjacent-transcripts (:transcripts result) false)]
    (html (layout (results-section :transcripts transcripts
                                   :current-date start-date)
                  :title (display-title channel start-date)
                  :servers-channels (servers-channels)
                  :current-server server
                  :current-channel channel))))

(defn search-handler
  "Specify search criteria by query parameters:
   :q query
   :server
   :channel (with or without hash)
   :sort - new, old, or score (default is score)"
  [{{:keys [q server channel sort]} :params}]
  (let [channel (normalize-channel channel)
        result (search :server server
                       :channel channel
                       :query-string q
                       :sort (keyword (or sort :score))
                       :from 0
                       :size *transcripts-per-page*)
        transcripts (:transcripts result)]
    (html (layout (results-section :transcripts transcripts)
                  :title "IRC Search"
                  :servers-channels (servers-channels)
                  :query q))))