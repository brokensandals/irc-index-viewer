(ns irc-index-viewer.search
  (:require [cheshire.core :as json]
            [clj-http.client :as client]
            [clj-time.core :as time]
            [clj-time.format :as time-format]
            [clojure.tools.logging :as log]
            [clojure.string :as string])
  (:use irc-index-viewer.config
        slingshot.core))

(def log-time-formatter (time-format/formatters :date-time-no-ms))

(defn- perform-query
  "Given a map containing the body for a search request,
   sends it to the index and returns the parsed response."
  [query]
  (try
    (let [response (client/post (str *index* "/transcript/_search")
                     {:content-type "application/json"
                      :body (json/generate-string query)})
          parsed (json/parse-string (:body response) true)]
      (cond
        (true? (:timed_out parsed))
          (do (log/warn "Query timed out. Query:" query "response:" response)
              nil)
        (not (= 0 (get-in parsed [:_shards :failed])))
          (do (log/warn "Query reported failed shards. Query:" query "response:" response)
              nil)
        true parsed))
    (catch Exception ex
      (log/warn ex "Query failed. Query:" query)
      nil)))

(defn- find-field-values
  "Look up the values for a field, using a facet query."
  ;FIXME: how many will elasticsearch return by default? can I override it?
  [field filter-term]
  (let [base-query {:facets {field {:terms {:field field}}}
                    :size 0}
        query (if filter-term
                  (assoc-in base-query [:facets field :facet_filter :term] filter-term)
                  base-query)
        response (perform-query query)]
    (if response
        (map :term (get-in response [:facets field :terms]))
        nil)))

; FIXME: presumably this is absurdly inefficient. But it's convenient and my
; usage of this app will have very low traffic, so...
(defn servers-channels
  "Returns a map of all known server names to lists of all known
   channels for those servers."
  []
  (into {}
    (for [server (find-field-values :server nil)]
      [server (find-field-values :channel {:server server})])))

(defn- sort-spec
  "Create the value for the \"sort\" field in a query."
  [sort]
  (case sort
    :new [{:log_time {:order "desc"}}]
    :old [{:log_time {:order "asc"}}]
    :score [:_score]))

(defn- parse-entry-times
  "Replace each entry's string :log_time value with a value parsed by clj-time."
  [entries]
  (for [entry entries]
    (assoc entry :log_time
      (time-format/parse log-time-formatter (:log_time entry)))))

(defn search
  [& {:keys [query-string server channel sort from size log-time-range]}]\
  (let [query-spec (if-not (string/blank? query-string)
                           {:query_string {:query query-string}}
                           {:match_all {}})
        filter-specs (filter identity
                       [(when-not (string/blank? server) {:term {:server server}})
                       (when-not (string/blank? channel) {:term {:channel channel}})
                       (when log-time-range
                         {:range
                           {:log_time
                             {:from (time-format/unparse log-time-formatter (time/start log-time-range))
                              :to (time-format/unparse log-time-formatter (time/end log-time-range))}}})])
        filter-spec (case (count filter-specs)
                      0 nil
                      1 (first filter-specs)
                      {:and {:filters filter-specs}})
        filtered-query-spec (when filter-spec
                              {:filtered {:query query-spec :filter filter-spec}})
        query {:query (or filtered-query-spec query-spec)
               :from from
               :size size
               :sort (sort-spec sort)}
        response (perform-query query)]
    (when response
      {:transcripts
        (for [transcript (map :_source (get-in response [:hits :hits]))]
          (update-in transcript [:entries] parse-entry-times))
       :total
        (get-in response [:hits :total])})))