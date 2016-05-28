(ns ru.jms.testingtool.data
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [alandipert.enduro :as e]
            [ru.jms.testingtool.shared.model :as m]
            [com.rpl.specter :as s]
            [ru.jms.testingtool.shared.common-utils :as cu]))

(defrecord MessagesStoreType [store buffer]
  m/MessagesStore
  (init-messages! [this collection-id messages]
    (if (= collection-id :buffer)
      (reset! (:buffer this) messages)
      (e/swap! (:store this) assoc-in [collection-id] messages)))
  (add-message! [this collection-id message]
    (if (not= collection-id :buffer)
      (e/swap! (:store this) #(s/setval [(s/keypath collection-id) s/END] [message] %))))
  (update-message! [this collection-id message-id message]
    (e/swap! (:store this) #(s/setval [(s/keypath collection-id) (cu/ALL-GET-BY-ID message-id)] message %)))
  (get-messages [this collection-id]
    (if (= collection-id :buffer)
      @(:buffer this)
      (get @(:store this) collection-id)))
  (get-messages-list [this collection-id id-list]
    (if (= collection-id :buffer)
      (s/select [(cu/ALL-GET-BY-ID-LIST id-list)] @(:buffer this))
      (s/select [(s/keypath collection-id) (cu/ALL-GET-BY-ID-LIST id-list)] @(:store this))))
  (get-message [this collection-id message-id]
    (if (= collection-id :buffer)
      (s/select-first [(cu/ALL-GET-BY-ID message-id)] @(:buffer this))
      (s/select-first [(s/keypath collection-id) (cu/ALL-GET-BY-ID message-id)] @(:store this))))
  (remove-messages! [this collection-id id-list]
    (if (not= collection-id :buffer)
      (let [store (:store this)
            messages (get @store collection-id)
            filtered-messages (remove #(contains? id-list (:id %)) messages)
            ]
        (e/swap! store assoc collection-id filtered-messages)))))

(def config
  (-> "config.edn"
      ;io/resource
      io/file
      slurp
      read-string))

(def config-data
  (atom (m/map->ConfigType config)))

(defn update-config! [config]
  (let [writer (clojure.java.io/writer "config.edn")]
    (clojure.pprint/pprint config writer)
    (.close writer)
    )
  (reset! config-data (m/map->ConfigType config)))

(def messages-data
  (MessagesStoreType. (e/file-atom {} "messages.clj" :pending-dir "tmp") (atom [])))






