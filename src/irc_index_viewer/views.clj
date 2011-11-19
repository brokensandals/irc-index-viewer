(ns irc-index-viewer.views
  (:require [clj-time.core :as time]
            [clj-time.format :as time-format]
            [clojure.string :as string])
  (:use [hiccup.core :only [escape-html]]
        [hiccup.page-helpers :only [url]]
        irc-index-viewer.config
        irc-index-viewer.path
        net.cgrand.enlive-html))

(def entry-time-formatter (time-format/formatter "HH:mm:ss"))
(def entry-date-formatter (time-format/formatter "MMMM d, yyyy"))
(def title-date-formatter (time-format/formatter "yyyy-MM-dd"))

(defn- insert-time
  "Add :time key, containing displayable time, to an entry."
  [entry]
  (assoc entry :time (time-format/unparse entry-time-formatter (:log_time entry))))

(defn- insert-description
  "Add :description key, containing displayable text of the entry (excluding nick) as HTML."
  [{:keys [event message] :as entry}]
  (assoc entry :description
    (case event
      "message" (escape-html message)
      "join" "joined"
      "part" (if (string/blank? message)
                 "left"
                 (str "left - " (escape-html message)))
      "quit" (if (string/blank? message)
                 "quit"
                 (str "quit - " (escape-html message)))
      "kick" (str "kicked " (escape-html message))
      "topic" (str "changed the topic - <span class=\"topic\">"
                   (escape-html message) "</span>"))))

(defn- insert-element-id
  "Add an :element-id key to the entry, unless the previous entry occurred
   at the same time (meaning it would have the :element-id attribute)."
  [{:keys [log_time] :as entry} prev-entry]
  (if (= log_time (:log_time prev-entry))
      entry
      (assoc entry :element-id
        (time-format/unparse time-path-formatter log_time))))

(defn- format-entries
  "Add the :time and :description keys to a group of entries - see insert-time, insert-description.
   If add-ids is truthy, adds an :element-id key such as \"t01:20:59\" to the first entry at each particular second."
  [entries add-ids]
  (let [step-1 (map (comp insert-time insert-description) entries)]
    (if add-ids
        (map insert-element-id step-1 (cons nil step-1))
        step-1)))

(defn- ymd
  "Return a vector of the year, month, and day components of a clj-time datetime.
   This is a *really* lame way of comparing just the date segments of datetimes, but
   it'll do for now..."
   [date]
   [(time/year date) (time/month date) (time/day date)])

(defn- entry-ymd
  "Return a vector of the year, month, and day components of the log_time of an entry."
  [{:keys [log_time]}]
  (ymd log_time))

(defn- group-entries-by-date
  "Return a list of vectors whose first elements are a displayable date
   and whose second elements are all of the given entries which were logged
   on that date."
  [entries]
  (for [[[year month day] group] (sort (group-by entry-ymd entries))]
    [(time-format/unparse entry-date-formatter (time/date-time year month day))
     group]))

(defn- page-count
  "Translate a total result count to a page count."
  [total]
  (let [adjustment (if (= 0 (mod total *transcripts-per-page*)) 0 1)]
    (+ adjustment (int (/ total *transcripts-per-page*)))))

(defmacro clone-and-modify-for
  "Like clone-for, but allows modifying the new clone element prior to
   modifying its children."
  [seq-expr modification & transformation-spec]
  `(clone-for ~seq-expr
     (do->
       ~modification
       (transformation
         ~@transformation-spec))))

(defn recent-activity-title
  "Generate the title for a 'recent activity' page."
  [server channel]
  (if (string/blank? server)
      "IRC recent"
      (str (if (string/blank? channel) server channel)
           " recent")))

(defn display-title
  "Generate the title for a 'display' (date) page."
  [channel date]
  (str channel " "
       (time-format/unparse title-date-formatter date)))

(deftemplate layout
  "views/layout.html"
  [main-section
   & {:keys [title
             query
             servers-channels
             current-server
             current-channel]}]
  [:link] #(update-in % [:attrs :href] url) ;to add context path
  [:script] #(update-in % [:attrs :src] url) ;to add context path
  [:title] (content title)
  [:.search-form] (set-attr :action (path-to "search"))
  [:.query] (set-attr :value query)

  [:.server]
    (clone-and-modify-for [[server channels] (sort servers-channels)]
      (add-class (when (= current-server server) "current"))
      [:.server-link] (do-> (set-attr :href (server-path server))
                            (content server))
      [:.channel]
        (clone-and-modify-for [channel (sort channels)]
          (add-class (when (= current-channel channel) "current"))
          [:.channel-link] (do-> (set-attr :href (channel-path server channel))
                                 (content channel))))
  
  [:#main-section] (substitute main-section))

(defsnippet adjacent-dates-section
  "views/results.html" [:#adjacent-dates]
  [server channel prev next]
  [:.prev-date] (do-> (content (str "<< "
                                    (time-format/unparse title-date-formatter prev)))
                      (set-attr :href (date-path server channel prev)))
  [:.next-date] (do-> (content (str (time-format/unparse title-date-formatter next)
                                    " >>"))
                      (set-attr :href (date-path server channel next))))

(defsnippet results-section
  "views/results.html" [:#results]
  ; transcripts: the transcripts to display
  ; current-date: if non-nil, will put ids corresponding to the times (e.g. #t02:34:35) on each entry from that date
  ; paging-section: node to include for paging info (may be nil)
  [& {:keys [transcripts current-date paging-section adjacent-dates-section]}]
  [:#paging] (substitute paging-section)

  [:#adjacent-dates] (substitute adjacent-dates-section)

  [:.transcript]
    (clone-for [{:keys [server channel entries]} transcripts]
      [:.source-link] (set-attr :href (channel-path server channel))
      [:.source-server] (content server)
      [:.source-channel] (content channel)

      [:.date-group]
        (clone-for [[date date-entries] (group-entries-by-date entries)]
          [:.date] (do-> (content date)
                         (set-attr :href (date-path server channel (:log_time (first date-entries)))))
          [:.entry]
            (clone-and-modify-for [{:keys [element-id log_time time nick message description event]}
                                    (format-entries date-entries
                                                    (and current-date
                                                         (= (ymd current-date) (entry-ymd (first date-entries)))))]
              (do-> (add-class (str "event-" event))
                    (if element-id
                        (set-attr :id element-id)
                        identity))
              
              [:.time] (set-attr :href (time-path server channel log_time))
              [:.time :time] (content time)

              [:.nick] (content nick)
              [:.description] (html-content description)))))

(defsnippet paging-section-snippet
  "views/results.html" [:#paging]
  [& {:keys [omitted-prev omitted-next current pages uri params]}]

  [:.omitted-prev] (when omitted-prev identity)
  [:.omitted-next] (when omitted-next identity)

  [:.page]
    (clone-and-modify-for [page pages]
      (add-class (when (= current page) "current"))
      [:a] (do-> (content (str page))
                 (set-attr :href (url uri (assoc params :page page))))))

(defn paging-section
  [current total-results uri params]
  (let [total-pages (page-count total-results)
        start-page (max 1 (- current 4))
        stop-page (min total-pages (+ start-page 9))]
    (paging-section-snippet :omitted-prev (> start-page 1)
                            :omitted-next (< stop-page total-pages)
                            :current current
                            :pages (range start-page (+ stop-page 1))
                            :uri uri
                            :params params)))
