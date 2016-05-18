(ns ru.jms.testingtool.core
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [goog.events :as events]
            [goog.history.EventType :as EventType]
            [ru.jms.testingtool.main-page :refer [home-page]]
            [ru.jms.testingtool.utils :refer [js-println]]
            )
  (:import goog.History))

;; -------------------------
;; Views

(defn about-page []
  [:div [:h2 "JMS Testing tool"] [:h5 "Michael Zheludkov."]
   [:div [:a {:href "#/"} "go to the home page"]]])

(defn no-connection-page []
  [:div.container
   [:div.page-header
    [:h1 "Awaiting connection!"]]])

(defn current-page []
  [(case (session/get :current-page)
     :home-page home-page
     :about-page about-page
     :no-connection-page no-connection-page)])

;; -------------------------
;; Routes
(secretary/set-config! :prefix "#")

(secretary/defroute "/" []
                    (session/put! :current-page :home-page))

(secretary/defroute "/about" []
                    (session/put! :current-page :about-page))

;; -------------------------
;; History
;; must be called after routes have been defined
(defn hook-browser-navigation! []
  (doto (History.)
    (events/listen
      EventType/NAVIGATE
      (fn [event]
        (secretary/dispatch! (.-token event))))
    (.setEnabled true)))

;; -------------------------
;; Initialize app
(defn mount-root []
  (reagent/render [current-page] (.getElementById js/document "app")))

(defn init! []
  (hook-browser-navigation!)
  (mount-root))

;methods to route



