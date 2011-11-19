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

; this function is terrible, i apologize to humanity
(defn- recombine-transcripts-by-inactivity
  "Group all adjacent transcripts from the same channel,
   then split them at periods of inactivity of *trancript-split-threshold*"
   [transcripts]
   (let [processed (transient [])]
     (doseq [group (reverse (partition-by
                              (fn [{:keys [server channel]}] [server channel])
                              transcripts))]
       (loop [[entry & more] (apply concat (map :entries (reverse group)))
              prev-time nil
              entries (transient [])]
         (if-not entry
                 (conj! processed (assoc (first group) :entries (persistent! entries)))
                 (if (and prev-time
                          (time/after? (time/minus (:log_time entry) *transcript-split-threshold*) prev-time))
                     (do
                       (conj! processed (assoc (first group) :entries (persistent! entries)))
                       (recur more (:log_time entry) (transient [entry])))
                     (do
                       (conj! entries entry)
                       (recur more (:log_time entry) entries))))))
     (reverse (persistent! processed))))

(defn- filter-entries-by-range
  "Remove entries that fall outside a given clj-time interval.
   Used for 'display' pages, to hide entries outside the current date."
  [transcripts interval]
  (for [transcript transcripts]
    (assoc transcript :entries
      (filter #(time/within? interval (:log_time %))
              (:entries transcript)))))

(defn- combine-all-transcripts
  "Merge all given transcripts into one. Used for 'display' pages,
   where we know they're all consecutive, from the same channel, and sorted."
  [transcripts]
  (if (empty? transcripts)
      []
      [(assoc (first transcripts) :entries
         (apply concat (map :entries transcripts)))]))

(defn- parse-page
  "Parse a user-supplied page number to an int, or return 1."
  [page]
  (if-not page
          1
          (try (Integer/parseInt page)
            (catch NumberFormatException ex
              1))))

(defn- page-offset
  "Translate a 1-based page number to a search offset."
  [page]
  (* *transcripts-per-page* (- page 1)))

(defn recent-activity-handler
  "Searches for the newest transcripts, optionally restricted by
   server or server+channel."
  [{{:keys [page] :as params} :params uri :uri} [server raw-channel]]
  (let [channel (normalize-channel raw-channel)
        page-num (parse-page page)

        {:keys [transcripts total]}
          (search :server server
                  :channel channel
                  :from (page-offset page-num)
                  :size *transcripts-per-page*
                  :sort :new)]
    (html (layout (results-section :transcripts (recombine-transcripts-by-inactivity transcripts)
                                   :paging-section (paging-section page-num total uri params))
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
        transcripts (filter-entries-by-range
                      (combine-all-transcripts (:transcripts result)) range)]
    (html (layout (results-section :transcripts transcripts
                                   :current-date start-date

                                   :adjacent-dates-section
                                    (adjacent-dates-section server
                                                            channel
                                                            (time/minus start-date (time/days 1))
                                                            (time/plus start-date (time/days 1))))
                  :title (display-title channel start-date)
                  :servers-channels (servers-channels)
                  :current-server server
                  :current-channel channel))))

(defn search-handler
  "Specify search criteria by query parameters:
   :q query
   :server
   :channel (with or without hash)
   :sort - new, old, or score (default is score)
   :page - 1-indexed"
  [{{:keys [q server channel sort page] :as params} :params uri :uri}]
  (let [channel (normalize-channel channel)
        page-num (parse-page page)
        
        {:keys [transcripts total]}
          (search :server server
                  :channel channel
                  :query-string q
                  :sort (keyword (or sort :score))
                  :from (page-offset page-num)
                  :size *transcripts-per-page*)]
    (html (layout (results-section :transcripts transcripts
                                   :paging-section (paging-section page-num total uri params))
                  :title "IRC Search"
                  :servers-channels (servers-channels)
                  :query q))))