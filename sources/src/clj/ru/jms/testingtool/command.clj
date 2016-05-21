(ns ru.jms.testingtool.command
  (:require
    [ru.jms.testingtool.data :as data]
    [ru.jms.testingtool.mq :as mq]
    [ru.jms.testingtool.shared.model :as m])
  (:use ru.jms.testingtool.dispatcher)
  (:import (java.util UUID)))

(defn gen-id [message]
  (into message {:id (.toString (UUID/randomUUID))}))

(defn get-queue-and-connection [command]
  (m/get-queue @data/config-data (:connection-id command) (:queue-id command)))

;-------------------------
;commands

(defn create-config-command [config]
  {:direction :client :command ::init :config config})

(defn create-init-messages-command [messages collection-id]
  {:direction :client :command ::init-messages :messages messages :collection-id collection-id})

(defn create-new-message-command [msg collection-id]
  {:direction :client :command ::new-message :msg msg :collection-id collection-id})

;--------------------------
;process commands from client

(defn browse-queue! [command]
  (process-server-command (into command {:command ::browse-queue})))

(defn send-collection! [collection-id]
  (send-command! (create-init-messages-command (m/get-messages data/messages-data collection-id) collection-id)))

(defmethod process-server-command ::init [command]
  (println "init command! " command)
  (println "config" data/config)
  (send-command! (create-config-command (m/to-map @data/config-data)))
  (doall (for [collection (m/get-collections @data/config-data)
               :let [collection-id (:id collection)]]
           (send-collection! collection-id))))

(defmethod process-server-command ::browse-queue [command]
  (let [[connection queue] (get-queue-and-connection command)
        browse-type (:browse-type connection)
        messages (reverse (if (= browse-type :queue-consumer)
                            (mq/consume-queue connection queue)
                            (mq/browse-queue connection queue)))]
    (send-command! (create-init-messages-command messages :buffer))
    (m/init-messages! data/messages-data :buffer messages)))

(defmethod process-server-command ::remove-messages [command]
  (let [collection-id (:collection-id command)
        id-list (:id-list command)]
    (m/remove-messages! data/messages-data collection-id id-list)
    (send-collection! collection-id)))

(defmethod process-server-command ::move-buffer-to-collection [command]
  (let [collection-id (:collection-id command)
        messages (m/get-messages-list data/messages-data :buffer (:id-list command))]
    (doall (for [msg messages
                 :let [updated-msg (gen-id msg)]]
             (m/add-message! data/messages-data collection-id updated-msg)))
    (send-collection! collection-id)))

(defmethod process-server-command ::send-messages [command]
  (let [[connection queue] (get-queue-and-connection command)
        messages (m/get-messages-list data/messages-data (:collection-id command) (:id-list command))]
    (mq/send-messages! connection queue messages)
    (browse-queue! command)))

(defmethod process-server-command ::purge-queue [command]
  (let [[connection queue] (get-queue-and-connection command)]
    (mq/purge-queue! connection queue)
    (browse-queue! command)))

(defmethod process-server-command :default [command]
  ;(println "default command! " command)
  )

(defmethod process-server-command ::save-or-create-message [command]
  (let [message (:message command)
        collection-id (:collection-id command)
        id (:id message)]
    (if (nil? id)
      (let [message-with-id (into message {:id (.toString (UUID/randomUUID))})]
        (m/add-message! data/messages-data collection-id message-with-id)
        (send-command! (create-new-message-command message-with-id collection-id)))
      (do
        (m/update-message! data/messages-data collection-id id message)
        (send-command! (create-init-messages-command (m/get-messages data/messages-data collection-id) collection-id))))))

