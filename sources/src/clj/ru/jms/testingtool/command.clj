(ns ru.jms.testingtool.command
  (:require
    [ru.jms.testingtool.data :as data]
    [ru.jms.testingtool.mq :as mq]
    [ru.jms.testingtool.shared.model :as m]
    [alandipert.enduro :as e]
    )
  (:use ru.jms.testingtool.dispatcher)
  (:import (java.util UUID)))


(defn gen-id [message]
  (into message {:id (.toString (UUID/randomUUID))})
  )

;-------------------------
;commands

(defn create-config-command [config]
  {:direction :client :command ::init :config config})

(defn create-init-messages [messages collection-id]
  {:direction :client :command ::init-messages :messages messages :collection-id collection-id})

(defn create-new-message-command [msg collection-id]
  {:direction :client :command ::new-message :msg msg :collection-id collection-id})

;--------------------------
;process commands from client

(defn browse-queue [command]
  (dispatch-server (into command {:command ::browse-queue})))

(defn dispatch-collection [collection-id]
  (dispatch (create-init-messages (m/get-messages data/messages-data collection-id) collection-id)))

(defmethod dispatch-server ::init [command]
  (println "init command! " command)
  (println "config" data/config)
  (dispatch (create-config-command (m/to-map @data/config-data)))
  (doall (for [collection (m/get-collections @data/config-data)
               :let [collection-id (:id collection)]]
           (dispatch-collection collection-id))))

(defmethod dispatch-server ::browse-queue [command]
  ;(println "receive command! " command)
  (let [[connection queue] (m/get-queue @data/config-data (:connection-id command) (:queue-id command))
        messages (mq/browse-queue connection queue)]
    (dispatch (create-init-messages messages :buffer))
    (m/init-messages data/messages-data :buffer messages)))

(defmethod dispatch-server ::get-one-message [command]
  (let [[connection queue] (m/get-queue @data/config-data (:connection-id command) (:queue-id command))
        collection-id (:collection-id command)
        msg (mq/convert-message (mq/get-message connection queue))]
    (if (some? msg)
      (do
        (m/add-message data/messages-data collection-id msg)
        (dispatch (create-new-message-command msg collection-id))))))

(defmethod dispatch-server ::remove-messages [command]
  ;(println "remove-messages!!" command)
  (let [collection-id (:collection-id command)
        id-list (:id-list command)]
    (m/remove-messages data/messages-data collection-id id-list)
    (dispatch-collection collection-id)
    ))

(defmethod dispatch-server ::move-buffer-to-collection [command]
  ;(println "move-buffer-to-collection!!" command)
  (let [collection-id (:collection-id command)
        messages (m/get-messages-list data/messages-data :buffer (:id-list command))]
    (doall (for [msg messages
                 :let [updated-msg (gen-id msg)]]
             (m/add-message data/messages-data collection-id updated-msg)))
    (dispatch-collection collection-id)))

(defmethod dispatch-server ::send-messages [command]
  ;(println "send-messages!!" command)
  (let [[connection queue] (m/get-queue @data/config-data (:connection-id command) (:queue-id command))
        messages (m/get-messages-list data/messages-data (:collection-id command) (:id-list command))]
    ;(println "add! " messages)
    (mq/send-messages connection queue messages)
    (browse-queue command)))

(defmethod dispatch-server ::purge-queue [command]
  ;(println "send-messages!!" command)
  (let [[connection queue] (m/get-queue @data/config-data (:connection-id command) (:queue-id command))]
    ;(println "purge! ")
    (mq/purge-queue connection queue)
    (browse-queue command)))

(defmethod dispatch-server :default [command]
  ;(println "default command! " command)
  )

(defmethod dispatch-server ::save-or-create-message [command]
  ;(println "save or create message!!" command)
  (let [message (:message command)
        collection-id (:collection-id command)
        id (:id message)]
    (if (nil? id)
      (let [message-with-id (into message {:id (.toString (UUID/randomUUID))})]
        (m/add-message data/messages-data collection-id message-with-id)
        (dispatch (create-new-message-command message-with-id collection-id)))
      (do
        (m/update-message data/messages-data collection-id id message)
        (dispatch (create-init-messages (m/get-messages data/messages-data collection-id) collection-id))))))

(init-sente-handler dispatch)