(ns ru.jms.testingtool.dispatcher
  (:require [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :refer (sente-web-server-adapter)])
  (:import (java.net ConnectException)
           (java.io IOException)))

(def ^:dynamic SENDER_UID :sente/all-users-without-uid)


(let [{:keys [ch-recv send-fn ajax-post-fn ajax-get-or-ws-handshake-fn connected-uids]}
      (sente/make-channel-socket! sente-web-server-adapter {:user-id-fn :client-id})]
  (def ring-ajax-post ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk ch-recv)                                     ; ChannelSocket's receive channel
  (def chsk-send! send-fn)                                  ; ChannelSocket's send API fn
  (def connected-uids connected-uids)                       ; Watchable, read-only atom
  )


(defn make-dispatcher [dispatch-function]
  (fn event-msg-handler* [{uid :uid client-id :client-id id :id [_ command] :event, :as full-data}]
    (with-bindings {#'SENDER_UID uid}
      (if (some? command)
        (dispatch-function command)))))


(defn init-sente-handler [dispatch-function]
  (let [handler (make-dispatcher dispatch-function)]
    (sente/start-chsk-router! ch-chsk handler)))

(defmulti dispatch-server (fn [x] (x :command)))

(defmulti dispatch (fn [x] (x :direction)))

(defmethod dispatch :client [command]
  (chsk-send! SENDER_UID [(:command command) command]))

(defmethod dispatch :server [command]
  ;(println "processing command " command)
  (try
    (dispatch-server command)
    (catch IOException e
      (dispatch {:direction :client :command (keyword "ru.jms.testingtool.command" "add-log-entry") :message "Can't connect to queue!" :level "alert-danger"})
      (throw e)
      )
    (catch Exception e
      (dispatch {:direction :client :command (keyword "ru.jms.testingtool.command" "add-log-entry") :message (str "Error during processing request! " (.getMessage e)) :level "alert-danger"})
      (throw e)
      )

    ))

(defmethod dispatch :client-and-server [command]
  ;(println "processing command " command)
  (dispatch-server command))


