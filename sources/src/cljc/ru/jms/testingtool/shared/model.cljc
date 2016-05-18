(ns ru.jms.testingtool.shared.model
  (:require [ru.jms.testingtool.shared.common-utils :as cu]
            [com.rpl.specter :as s]))


(defprotocol MessagesStore
  (init-messages [this collection-id messages])
  (add-message [this collection-id message])
  (update-message [this collection-id message-id message])
  (get-messages [this collection-id])
  (get-messages-list [this collection-id id-list])
  (get-message [this collection-id message-id])
  (remove-messages [this collection-id id-list]))

(defprotocol ConfigStore
  (get-collections [this])
  (get-first-collection-id [this])
  (get-queue [this connection-id queue-id])
  (to-map [this])
  (get-collection [this collection-id]))

(defrecord ConfigType [connections collections]
  ConfigStore
  (get-collections [this]
    (:collections this))
  (get-first-collection-id [this]
    (get-in this [:collections 0 :id]))
  (get-queue [this connection-id queue-id]
    (let [connection (s/select-first [:connections (cu/ALL-GET-BY-ID connection-id)] this)
          queue (s/select-first [:queues (cu/ALL-GET-BY-ID queue-id)] connection)]
      [connection queue]))
  (to-map [this]
    (into {} this))
  (get-collection [this collection-id]
    (s/select-first [:collections (cu/ALL-GET-BY-ID collection-id)] this)))

(defn create-config [data]
  (ConfigType. (:connections data) (:collections data)))