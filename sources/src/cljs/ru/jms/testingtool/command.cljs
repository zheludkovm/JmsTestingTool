(ns ru.jms.testingtool.command
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.session :as session]
            [ru.jms.testingtool.utils :refer [js-println xor-assoc vec-remove]]
            [ru.jms.testingtool.data :as data]
            [ru.jms.testingtool.dispatcher :as dispatcher]
            [ru.jms.testingtool.shared.model :as m]
            [com.rpl.specter :as s]
            )
  )

(declare dispatch-log-entry)
(declare browse-queue)

(declare validate-current-page-number)

;--------------------
;process commands
;server commands
(defmethod dispatcher/dispatch-client ::init [command]
  (js-println "receive init!" command)
  (reset! data/config-data (m/create-config (:config command)))
  (swap! data/web-data assoc
         :selected-collection-id (m/get-first-collection-id @data/config-data)
         :selected-connection-id nil
         :selected-queue-id nil
         )
  (m/init-messages data/messages-data :buffer [])
  (js-println data/messages-data)
  )

(defmethod dispatcher/dispatch-client ::new-message [command]
  (js-println "receive new message!" command)
  (let [msg (:msg command)
        collection-id (:collection-id command)
        ]
    (m/add-message data/messages-data collection-id msg)))

(defmethod dispatcher/dispatch-client ::init-messages [command]
  (js-println "receive init message!" command)
  (let [messages (:messages command)
        collection-id (:collection-id command)
        collection-name (data/get-collection-name collection-id)
        ]
    (m/init-messages data/messages-data collection-id messages)
    (validate-current-page-number)
    (if (= collection-id :buffer)
      (swap! data/web-data assoc :checked-buffer-messages #{}))
    (dispatch-log-entry (str "receive " (count messages) " messages into collection " collection-name))))

;client commands

(defmethod dispatcher/dispatch-client ::select-queue [command]
  (xor-assoc data/web-data :selected-queue-id (:queue-id command))
  (swap! data/web-data assoc :selected-connection-id (:connection-id command))
  (m/init-messages data/messages-data :buffer [])
  (swap! data/web-data assoc :expanded-buffer-messages #{})
  (swap! data/web-data assoc :checked-buffer-messages #{})
  (if (some? (:selected-queue-id @data/web-data))
    (browse-queue)))

(defmethod dispatcher/dispatch-client ::add-log-entry [command]
  (let [log-entries (:log-entries @data/web-data)
        updated-log-entries (conj log-entries {:time (js/Date.) :text (:message command) :level (:level command)})]
    (swap! data/web-data assoc :log-entries updated-log-entries)))

(defmethod dispatcher/dispatch-client ::select-collection [command]
  (data/select-collection! (:id command)))

(defmethod dispatcher/dispatch-client ::remove-message-header [command]
  (let [idx (:idx command)
        headers (get-in @data/web-data [:edited-message :headers])
        changed-headers (vec-remove headers idx)]
    (swap! data/web-data assoc-in [:edited-message :headers] changed-headers)))

(defmethod dispatcher/dispatch-client ::add-message-header [command]
  (let [count (count (get-in @data/web-data [:edited-message :headers]))]
    (swap! data/web-data assoc-in [:edited-message :headers count] {})))

(defmethod dispatcher/dispatch-client ::expand-message [command]
  (let [message-id (:message-id command)
        expanded-set (:expanded-set command)]
    (swap! data/web-data update-in [expanded-set] conj message-id)))

(defmethod dispatcher/dispatch-client ::collapse-message [command]
  (let [message-id (:message-id command)
        expanded-set (:expanded-set command)]
    (swap! data/web-data update-in [expanded-set] disj message-id)))

(defmethod dispatcher/dispatch-client ::collapse-expand-all [command]
  (data/select-deselect-all! (:table-data command) :expanded-set))

(defmethod dispatcher/dispatch-client ::check-message [command]
  (let [message-id (:message-id command)
        checked-set-symb (:checked-set command)
        checked-set (checked-set-symb @data/web-data)
        is-checked (not (contains? checked-set message-id))
        op (if is-checked conj disj)
        new-checked-set (op checked-set message-id)]
    (swap! data/web-data assoc checked-set-symb new-checked-set)))

(defmethod dispatcher/dispatch-client ::remove-messages [command]
  (let [id-list (:id-list command)
        collection-id (:collection-id command)]
    (m/remove-messages data/messages-data collection-id id-list)
    (swap! data/web-data assoc :checked-collections-messages #{}))
  (validate-current-page-number))

(defmethod dispatcher/dispatch-client ::init-add-message [command]
  (swap! data/web-data assoc :edited-message {:type :string :headers []}))

(defmethod dispatcher/dispatch-client ::init-edit-message [command]
  (let [message-id (:message-id command)
        collection-id (data/get-selected-collection-id)
        message (m/get-message data/messages-data collection-id message-id)]
    (swap! data/web-data assoc :edited-message message)))

(defmethod dispatcher/dispatch-client ::select-deselect-all [command]
  (data/select-deselect-all! (:table-data command) :checked-set))

(defn validate-page-number [page]
  (let [count (data/total-buffer-pages)
        corrected-page (cond
                         (<= page 0) 0
                         (>= page count) (dec count)
                         :else page)]
    (if (= corrected-page -1) 0 corrected-page)))

(defn update-buffer-page-number [page]
  (swap! data/web-data assoc :buffer-page (validate-page-number page)))

(defmethod dispatcher/dispatch-client ::pager-action [command]
  (let [direction (:action command)
        current-page (:buffer-page @data/web-data)
        page (case direction
               :pager-fast-forward (data/total-buffer-pages)
               :pager-forward (inc current-page)
               :pager-backward (dec current-page)
               :pager-fast-backward 0)]
    (update-buffer-page-number page)))

(defn validate-current-page-number []
  (let [page (:buffer-page @data/web-data)]
    (update-buffer-page-number page)))

;------------------------
;gen commands

(defn dispatch-log-entry [message]
  (dispatcher/dispatch {:direction :client :command ::add-log-entry :message message}))

(defn dispatch-init []
  (dispatcher/dispatch {:direction :server :command ::init}))

;first init

(dispatcher/init-sente-client dispatch-init dispatcher/dispatch-client)


(defn get-one-message []
  (dispatcher/dispatch {:direction     :server
                        :command       ::get-one-message
                        :connection-id (:selected-connection-id @data/web-data)
                        :queue-id      (:selected-queue-id @data/web-data)
                        :collection-id (:selected-collection-id @data/web-data)}))

(defn browse-queue []
  (dispatcher/dispatch {:direction     :server
                        :command       ::browse-queue
                        :connection-id (:selected-connection-id @data/web-data)
                        :queue-id      (:selected-queue-id @data/web-data)}))

(defn purge-queue []
  (dispatcher/dispatch {:direction     :server
                        :command       ::purge-queue
                        :connection-id (:selected-connection-id @data/web-data)
                        :queue-id      (:selected-queue-id @data/web-data)})
  )

(defn remove-selected-messages []
  (dispatcher/dispatch {:direction     :server
                        :command       ::remove-messages
                        :id-list       (:checked-collections-messages @data/web-data)
                        :collection-id (:selected-collection-id @data/web-data)
                        }))

(defn save-or-create-message []
  (dispatcher/dispatch {:direction     :server
                        :command       ::save-or-create-message
                        :message       (:edited-message @data/web-data)
                        :collection-id (:selected-collection-id @data/web-data)}))

(defn send-messages []
  (dispatcher/dispatch {:direction     :server
                        :command       ::send-messages
                        :id-list       (:checked-collections-messages @data/web-data)
                        :collection-id (:selected-collection-id @data/web-data)
                        :queue-id      (:selected-queue-id @data/web-data)
                        :connection-id (:selected-connection-id @data/web-data)}))

(defn move-buffer-to-collection []
  (dispatcher/dispatch {:direction     :server
                        :command       ::move-buffer-to-collection
                        :id-list       (:checked-buffer-messages @data/web-data)
                        :collection-id (:selected-collection-id @data/web-data)}))

(defn exec-client [command & params]
  (dispatcher/dispatch (into {:direction :client
                              :command   (keyword "ru.jms.testingtool.command" (name command))}
                             (map vec (partition 2 params)))))

