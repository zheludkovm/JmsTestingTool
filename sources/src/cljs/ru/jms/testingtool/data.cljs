(ns ru.jms.testingtool.data
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.session :as session]
            [ru.jms.testingtool.utils :refer [js-println xor-assoc]]

            [cljs.core.async :as async :refer (<! >! put! chan)]
            [taoensso.sente :as sente :refer (cb-success?)]
            [taoensso.encore :as enc :refer (tracef debugf infof warnf errorf)]
            [ru.jms.testingtool.shared.model :as m]
            [com.rpl.specter :as s]
            [ru.jms.testingtool.shared.common-utils :as cu]
            [ru.jms.testingtool.utils :as u]
            [clojure.string :as str]
            )
  )


(defrecord MessagesStoreType [store]
  m/MessagesStore
  (init-messages [this collection-id messages]
    (swap! (:store this) assoc-in [collection-id] messages))
  (add-message [this collection-id message]
    (swap! (:store this) #(s/setval [(s/keypath collection-id) s/END] [message] %)))
  (update-message [this collection-id message-id message]
    (swap! (:store this) #(s/setval [(s/keypath collection-id) (cu/ALL-GET-BY-ID message-id)] message %)))
  (get-messages [this collection-id]
    (get @(:store this) collection-id))
  (get-message [this collection-id message-id]
    (s/select-first [(s/keypath collection-id) (cu/ALL-GET-BY-ID message-id)] @(:store this)))
  (remove-messages [this collection-id id-list]
    (let [messages (get @store collection-id)
          filtered-messages (remove #(contains? id-list (:id %)) messages)]
      (swap! (:store this) assoc collection-id filtered-messages))))

(def config-data
  (reagent/atom
    (m/create-config
      {:connections [
                     ;{:id :1 :title "connection1" :queues [
                     ;                                      {:id 1 :title "queue1111"}
                     ;                                      {:id 2 :title "queue2"}
                     ;                                      ]}
                     ;{:id :2 :title "connection2" :queues [
                     ;                                      {:id 3 :title "queue21111"}
                     ;                                      {:id 4 :title "queue221111"}
                     ;                                      ]}
                     ]

       :collections [
                     ;{:id   "1"
                     ; :name "buffer"
                     ; }
                     ;{:id   "2"
                     ; :name "coll2"
                     ; }
                     ]
       }
      )))

; collection-id -> [message]
(def messages-data
  (MessagesStoreType. (reagent/atom {
                                     ;:buffer [{:id               "1111"
                                     ;          :jmsMessageId     "ID:JMS id 1"
                                     ;          :type             :string
                                     ;          :short-title      "<message1 ..."
                                     ;          :long-title       "<message1 id=11>\n temp </message1>  ..."
                                     ;          :headers          [{:name "header1" :value "str-header" :type :string}
                                     ;                             {:name "header2" :value 11 :type :long}
                                     ;                             {:name "header3" :value true :type :boolean}]
                                     ;          :size             111
                                     ;          :jmsCorrelationId "corr-id"
                                     ;          :jmsExpiration    10
                                     ;          :jmsPriority      1
                                     ;          :jmsTimestamp     11111111
                                     ;          }
                                     ;         {:id               "2222"
                                     ;          :jmsMessageId     "ID:JMS id 2"
                                     ;          :type             :string
                                     ;          :short-title      "<message2 ..."
                                     ;          :long-title       "<message2 id=22>\n temp </message2>  ..."
                                     ;          :headers          [{:name "header1" :value "str-header" :type :string}
                                     ;                             {:name "header2" :value 11 :type :long}
                                     ;                             {:name "header3" :value true :type :boolean}]
                                     ;          :size             222
                                     ;          :jmsCorrelationId "corr-id2"
                                     ;          :jmsExpiration    10
                                     ;          :jmsPriority      1
                                     ;          :jmsTimestamp     11111111
                                     ;          }
                                     ;         ]
                                     ;"2"     [{:id               "333"
                                     ;          :jmsMessageId     "ID:JMS id 1"
                                     ;          :type             :string
                                     ;          :short-title      "<message1 ..."
                                     ;          :long-title       "<message1 id=11>\n temp </message1>  ..."
                                     ;          :headers          [{:name "header1" :value "str-header" :type :string}
                                     ;                             {:name "header2" :value 11 :type :long}
                                     ;                             {:name "header3" :value true :type :boolean}]
                                     ;          :size             111
                                     ;          :jmsCorrelationId "corr-id"
                                     ;          :jmsExpiration    10
                                     ;          :jmsPriority      1
                                     ;          :jmsTimestamp     11111111
                                     ;          }
                                     ;         ]
                                     ;"3"     [{:id               "444"
                                     ;          :jmsMessageId     "ID:JMS id 000"
                                     ;          :type             :string
                                     ;          :short-title      "<message1 ..."
                                     ;          :long-title       "<message1 id=11>\n temp </message1>  ..."
                                     ;          :headers          [{:name "header1" :value "str-header" :type :string}
                                     ;                             {:name "header2" :value 11 :type :long}
                                     ;                             {:name "header3" :value true :type :boolean}]
                                     ;          :size             111
                                     ;          :jmsCorrelationId "corr-id"
                                     ;          :jmsExpiration    10
                                     ;          :jmsPriority      1
                                     ;          :jmsTimestamp     11111111
                                     ;          }
                                     ;         {:id               "555"
                                     ;          :jmsMessageId     "ID:JMS id 111"
                                     ;          :type             :string
                                     ;          :short-title      "<message1 ..."
                                     ;          :long-title       "<message1 id=11>\n temp </message1>  ..."
                                     ;          :headers          [{:name "header1" :value "str-header" :type :string}
                                     ;                             {:name "header2" :value 11 :type :long}
                                     ;                             {:name "header3" :value true :type :boolean}]
                                     ;          :size             111
                                     ;          :jmsCorrelationId "corr-id"
                                     ;          :jmsExpiration    10
                                     ;          :jmsPriority      1
                                     ;          :jmsTimestamp     11111111
                                     ;          }
                                     ;         ]

                                     })))

(def BUFFER_PAGE_SIZE 10)

(def web-data (reagent/atom
                {:selected-queue-id            nil
                 :selected-connection-id       nil
                 :selected-collection-id       nil

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
                 }
                ))


;support web data functions
(defn get-selected-queue-id []
  (:selected-queue-id @web-data))

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
  (swap! web-data assoc :checked-collections-messages #{}))

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