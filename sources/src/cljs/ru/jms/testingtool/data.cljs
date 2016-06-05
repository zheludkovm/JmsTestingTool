(ns ru.jms.testingtool.data
  (:require [reagent.core :as reagent :refer [atom]]
            [ru.jms.testingtool.utils :refer [js-println xor-assoc]]
            [ru.jms.testingtool.shared.model :as m]
            [com.rpl.specter :as s]
            [ru.jms.testingtool.shared.common-utils :as cu]
            [ru.jms.testingtool.utils :as u]))

(defrecord MessagesStoreType [store]
  m/MessagesStore
  (init-messages! [this collection-id messages]
    (swap! (:store this) assoc-in [collection-id] messages))
  (add-message! [this collection-id message]
    (swap! (:store this) #(s/setval [(s/keypath collection-id) s/END] [message] %)))
  (update-message! [this collection-id message-id message]
    (swap! (:store this) #(s/setval [(s/keypath collection-id) (cu/ALL-GET-BY-ID message-id)] message %)))
  (get-messages [this collection-id]
    (get @(:store this) collection-id))
  (get-message [this collection-id message-id]
    (s/select-first [(s/keypath collection-id) (cu/ALL-GET-BY-ID message-id)] @(:store this)))
  (remove-messages! [this collection-id id-list]
    (let [messages (get @store collection-id)
          filtered-messages (remove #(contains? id-list (:id %)) messages)]
      (swap! (:store this) assoc collection-id filtered-messages))))

(def config-data
  (reagent/atom
    (m/map->ConfigType
      {:connections []
       :collections []})))

; collection-id -> [message]
(def messages-data
  (MessagesStoreType. (reagent/atom {})))

(def BUFFER_PAGE_SIZE 10)

(def web-data (reagent/atom
                {:selected-queue-id            nil
                 :selected-connection-id       nil
                 :selected-collection-id       nil
                 :expanded-connection-id       nil

                 :log-entries                  '()
                 :edited-message               {}
                 ;buffer
                 :expanded-buffer-messages     #{}
                 :checked-buffer-messages      #{}
                 :buffer-filter                ""
                 :buffer-page                  0

                 ;collection
                 :expanded-collection-messages #{}
                 :checked-collections-messages #{}
                 :collection-filter            ""
                 ;edited config
                 :edited-config                {}
                 :edited-connection-id         nil
                 }))


;support web data functions
(defn get-selected-queue-id []
  (:selected-queue-id @web-data))

(defn get-edited-connection-idx []
  (:edited-connection-idx @web-data))

(defn get-selected-collection-id []
  (if-let [id (:selected-collection-id @web-data)]
    id
    (m/get-first-collection-id @config-data)))

(defn get-collection-name [collection-id]
  (:name (m/get-collection @config-data collection-id)))

(defn get-selected-collection-name []
  (get-collection-name (get-selected-collection-id)))

(defn non-buffer-collections []
  (filterv #(not= (:id %) :buffer) (:collections @config-data)))

(defn get-non-buffer-collection-id [index]
  (get-in (non-buffer-collections) [index :id]))

;messages functions

(defn selected-messages []
  (m/get-messages messages-data (get-selected-collection-id)))

(defn buffer-messages []
  (m/get-messages messages-data :buffer))

(defn sorted-connections []
  (sort-by :title (:connections @config-data)))

(defn sorted-queues [connection]
  (->> (:queues connection)
       (sort-by #(u/or-property % :title :name))))

(defn filter-messages [messages filter-field-in-webdata filter-field-in-message]
  (filter (u/contains-or-empty? filter-field-in-message (filter-field-in-webdata @web-data)) messages))

(defn filtered-collection-messages []
  (filter-messages (selected-messages) :collection-filter :short-title))

(defn filtered-buffer-messages []
  (filter-messages (buffer-messages) :buffer-filter :long-title))

(defn partionitoned-buffer-messages []
  (let [messages (filtered-buffer-messages)]
    (partition BUFFER_PAGE_SIZE BUFFER_PAGE_SIZE nil messages)))

(defn paged-buffer-messages []
  (let [page (:buffer-page @web-data)
        partitioned-messages (partionitoned-buffer-messages)]
    (if (not= 0 (count partitioned-messages))
      (nth partitioned-messages page)
      '())))

(defn total-buffer-pages []
  (count (partionitoned-buffer-messages)))

;update methods

(defn select-collection! [id]
  (swap! web-data assoc :selected-collection-id id)
  (swap! web-data assoc :checked-collections-messages #{})
  (swap! web-data assoc :expanded-collection-messages #{}))

(defn get-checked-size [table-data checked-set-name]
  (let [checked-set (checked-set-name table-data)]
    (count (checked-set @web-data))))

(defn all-selected? [table-data checked-set-name]
  (let [checked-size (get-checked-size table-data checked-set-name)
        all-size (count ((:all-messages-func table-data)))]
    (and (not= all-size 0) (= all-size checked-size))))

(defn has-selected? [table-data checked-set-name]
  (not= 0 (get-checked-size table-data checked-set-name)))

(defn select-deselect-all! [table-data checked-set-name]
  (let [checked-set (checked-set-name table-data)
        all-id-set (set (map :id ((:all-messages-func table-data))))]
    (if (not (has-selected? table-data checked-set-name))
      (swap! web-data assoc checked-set all-id-set)
      (swap! web-data assoc checked-set #{}))))

;pager methods

(defn validate-page-number [page]
  (let [count (total-buffer-pages)
        corrected-page (cond
                         (<= page 0) 0
                         (>= page count) (dec count)
                         :else page)]
    (if (= corrected-page -1) 0 corrected-page)))

(defn update-buffer-page-number! [page]
  (swap! web-data assoc :buffer-page (validate-page-number page)))

(defn validate-current-page-number! []
  (let [page (:buffer-page @web-data)]
    (update-buffer-page-number! page)))

; config edit

(defn prepare-config-for-edit! []
  (swap! web-data assoc :edited-config (into {} @config-data)))