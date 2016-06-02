(ns ru.jms.testingtool.handler
  (:require [compojure.core :refer [GET POST defroutes]]
            [compojure.route :refer [not-found resources]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [hiccup.core :refer [html]]
            [hiccup.page :refer [include-js include-css]]
            [prone.middleware :refer [wrap-exceptions]]
            [ring.middleware.reload :refer [wrap-reload]]
            [environ.core :refer [env]]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :refer (sente-web-server-adapter)]))

(let [{:keys [ch-recv ajax-post-fn ajax-get-or-ws-handshake-fn]}
      (sente/make-channel-socket! sente-web-server-adapter {:user-id-fn :client-id})]
  (def ring-ajax-post ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk ch-recv))

(defn init-sente-handler [dispatch-function]
  (sente/start-chsk-router! ch-chsk (fn event-msg-handler* [{send-fn     :send-fn
                                                             uid         :uid
                                                             [_ command] :event
                                                             :as         full-data}]
                                      (dispatch-function uid send-fn command))))

(def home-page
  (html
    [:html
     [:head
      [:meta {:charset "utf-8"}]
      [:meta {:name    "viewport"
              :content "width=device-width, initial-scale=1"}]
      [:link {:href "css/color-brewer.min.css" :rel "stylesheet"}]
      [:link {:href "css/local.css" :rel "stylesheet"}]
      [:link {:href "css/bootstrap.min.css" :rel "stylesheet"}]
      [:link {:href "css/local-after.css" :rel "stylesheet"}]
      [:link {:href "css/raven.css" :rel "stylesheet"}]
      [:link {:href "css/animate.min.css" :rel "stylesheet"}]
      ]
     [:body
      (include-js "js/jquery-2.1.1.min.js")
      (include-js "js/bootstrap.min.js")
      [:div#app
       [:h3 "ClojureScript has not been compiled!"]
       [:p "please run "
        [:b "lein figwheel"]
        " in order to start the compiler"]]
      (include-js "js/app.js")]]))

(defroutes routes
           (GET "/chsk" req (ring-ajax-get-or-ws-handshake req))
           (POST "/chsk" req (ring-ajax-post req))
           (GET "/" [] home-page)
           (resources "/")
           (not-found "Not Found"))

(def app
  (let [r (-> #'routes
              ring.middleware.keyword-params/wrap-keyword-params
              ring.middleware.params/wrap-params)
        handler (wrap-defaults r site-defaults)]
    (if (env :dev) (-> handler
                       wrap-exceptions
                       wrap-reload)
                   handler)))

