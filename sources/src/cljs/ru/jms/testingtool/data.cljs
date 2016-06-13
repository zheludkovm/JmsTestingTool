(ns ru.jms.testingtool.data
  (:require [reagent.core :as reagent :refer [atom]]
            [ru.jms.testingtool.utils :refer [js-println xor-assoc in?]]
            [ru.jms.testingtool.shared.model :as m]
            [com.rpl.specter :as s]
            [ru.jms.testingtool.shared.common-utils :as cu]
            [ru.jms.testingtool.utils :as u]
            [clojure.set :as set]))

(defrecord MessagesStoreType [store]
  m/MessagesStore
  (init-messages! [this collection-id messages]
    (swap! store assoc-in [collection-id] messages))
  (add-message! [this collection-id message]
    (swap! store #(s/setval [(s/keypath collection-id) s/END] [message] %)))
  (update-message! [this collection-id message-id message]
    (swap! store #(s/setval [(s/keypath collection-id) (cu/ALL-GET-BY-ID message-id)] message %)))
  (get-messages [this collection-id]
    (get @store collection-id))
  (get-message [this collection-id message-id]
    (s/select-first [(s/keypath collection-id) (cu/ALL-GET-BY-ID message-id)] @store))
  (remove-messages! [this collection-id id-list]
    (let [messages (get @store collection-id)
          filtered-messages (remove #(contains? id-list (:id %)) messages)]
      (swap! store assoc collection-id filtered-messages))))

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
                 ;transfer
                 :transfer-collection-id       nil
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
  (if (= collection-id :buffer)
    "Buffer"
    (:name (m/get-collection @config-data collection-id))))

(defn get-selected-collection-name []
  (get-collection-name (get-selected-collection-id)))

(defn get-transfer-collection-name []
  (get-collection-name (:transfer-collection-id @web-data)))

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
  (sort-by :short-title (filter-messages (selected-messages) :collection-filter :short-title)))

(defn filtered-buffer-messages []
  (reverse (filter-messages (buffer-messages) :buffer-filter :long-title)))

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

(defn get-checked-size [checked-set]
  (count (checked-set @web-data)))

;(defn all-selected? [table-data checked-set-name]
;  (let [checked-size (get-checked-size  (checked-set-name table-data) )
;        all-size (count ((:all-messages-func table-data)))]
;    (and (not= all-size 0) (= all-size checked-size))))

(defn has-selected? [checked-set]
  (not= 0 (get-checked-size checked-set)))

(defn select-deselect-all! [all-messages-func checked-set]
  (let [all-id-set (set (map :id (all-messages-func)))]
    (if (not (has-selected? checked-set))
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
  (swap! web-data assoc
         :edited-config (into {} @config-data)
         :edited-connection-idx (if (empty? (:connections @config-data)) nil 0)))

(defn copy-current-collection-id []
  (swap! web-data #(assoc % :transfer-collection-id (:selected-collection-id %))))

(defn check-selection! []
  (if-let [checked-msg-id (first (:checked-collections-messages @web-data))]
    (if (not (in? (map :id (filtered-collection-messages)) checked-msg-id))
      (swap! web-data assoc :checked-collections-messages #{}))))

(defn check-expanded! []
  (let [expanded-collection-id-list (:expanded-collection-messages @web-data)
        all-id-list (map :id (filtered-collection-messages))
        remove-msg-list (set (filter #(u/not-in? all-id-list %) expanded-collection-id-list))]
    (if (not-empty remove-msg-list)
      (swap! web-data update :expanded-collection-messages set/difference remove-msg-list))))

(defn check-all! []
  (check-selection!)
  (check-expanded!))

; scan if selected messge visible
(add-watch web-data :collection-filter check-all!)