(ns irc-index-viewer.core
  (:use hiccup.middleware
        irc-index-viewer.config
        irc-index-viewer.controller
        net.cgrand.moustache
        ring.middleware.content-type
        ring.middleware.keyword-params
        ring.middleware.params
        ring.middleware.resource))

(defn- integer-segment
  [segment]
  (try
    (Integer/parseInt segment)
    (catch NumberFormatException ex)))

; See http://groups.google.com/group/clojure-web-dev/browse_thread/thread/9be1f7667cc38f73
(defn- wrap-remove-context-path
  "Remove the context path from the URI when present."
  [handler]
  (fn [{:keys [path-info] :as req}]
    (handler
      (if path-info
          (assoc req :uri path-info)
          req))))

(def handler
  (app wrap-base-url
       wrap-remove-context-path
       wrap-current-configuration
       wrap-params
       wrap-keyword-params
       (wrap-resource "public")
       wrap-content-type
    
    ["search"] {:get search-handler}
    
    [server channel [year integer-segment] [month integer-segment] [day integer-segment]]
      {:get (delegate display-handler server channel year month day)}
    
    [& details] {:get (delegate recent-activity-handler details)}))