(ns ru.jms.testingtool.command
  (:require [ru.jms.testingtool.utils :refer [js-println xor-assoc vec-remove]]
            [ru.jms.testingtool.data :as data]
            [ru.jms.testingtool.dispatcher :refer [send-command! process-client-command]]
            [ru.jms.testingtool.shared.model :as m]))

(declare browse-queue!)
(declare add-log-entry!)
;--------------------
;process server commands

(defmethod process-client-command ::init [command]
  (js-println "receive init!" command)
  (reset! data/config-data (m/create-config (:config command)))
  (swap! data/web-data assoc
         :selected-collection-id (m/get-first-collection-id @data/config-data)
         :selected-connection-id nil
         :selected-queue-id nil)
  (m/init-messages! data/messages-data :buffer [])
  (js-println data/messages-data))

(defmethod process-client-command ::new-message [command]
  (js-println "receive new message!" command)
  (let [msg (:msg command)
        collection-id (:collection-id command)]
    (m/add-message! data/messages-data collection-id msg)))

(defmethod process-client-command ::init-messages [command]
  (js-println "receive init message!" command)
  (let [messages (:messages command)
        collection-id (:collection-id command)
        collection-name (data/get-collection-name collection-id)]
    (m/init-messages! data/messages-data collection-id messages)
    (data/validate-current-page-number!)
    (if (= collection-id :buffer)
      (swap! data/web-data assoc :checked-buffer-messages #{}))
    (add-log-entry! (str "receive " (count messages) " messages into collection " collection-name))))

(defmethod process-client-command ::remove-messages [command]
  (let [id-list (:id-list command)
        collection-id (:collection-id command)]
    (m/remove-messages! data/messages-data collection-id id-list)
    (swap! data/web-data assoc :checked-collections-messages #{})))

;client commands

(defmethod process-client-command ::select-queue [command]
  (xor-assoc data/web-data :selected-queue-id (:queue-id command))
  (swap! data/web-data assoc :selected-connection-id (:connection-id command))
  (m/init-messages! data/messages-data :buffer [])
  (swap! data/web-data assoc :expanded-buffer-messages #{})
  (swap! data/web-data assoc :checked-buffer-messages #{})
  (if (some? (:selected-queue-id @data/web-data))
    (browse-queue!)))

(defmethod process-client-command ::add-log-entry [command]
  (let [log-entries (:log-entries @data/web-data)
        updated-log-entries (conj log-entries {:time (js/Date.) :text (:message command) :level (:level command)})]
    (swap! data/web-data assoc :log-entries updated-log-entries)))

(defmethod process-client-command ::select-collection [command]
  (data/select-collection! (:id command)))

(defmethod process-client-command ::remove-message-header [command]
  (let [idx (:idx command)
        headers (get-in @data/web-data [:edited-message :headers])
        changed-headers (vec-remove headers idx)]
    (swap! data/web-data assoc-in [:edited-message :headers] changed-headers)))

(defmethod process-client-command ::add-message-header [command]
  (let [count (count (get-in @data/web-data [:edited-message :headers]))]
    (swap! data/web-data assoc-in [:edited-message :headers count] {})))

(defmethod process-client-command ::expand-message [command]
  (let [message-id (:message-id command)
        expanded-set (:expanded-set command)]
    (swap! data/web-data update-in [expanded-set] conj message-id)))

(defmethod process-client-command ::collapse-message [command]
  (let [message-id (:message-id command)
        expanded-set (:expanded-set command)]
    (swap! data/web-data update-in [expanded-set] disj message-id)))

(defmethod process-client-command ::collapse-expand-all [command]
  (data/select-deselect-all! (:table-data command) :expanded-set))

(defmethod process-client-command ::check-message [command]
  (let [message-id (:message-id command)
        checked-set-symb (:checked-set command)
        checked-set (checked-set-symb @data/web-data)
        is-checked (not (contains? checked-set message-id))
        op (if is-checked conj disj)
        new-checked-set (op checked-set message-id)]
    (swap! data/web-data assoc checked-set-symb new-checked-set)))

(defmethod process-client-command ::init-add-message [command]
  (swap! data/web-data assoc :edited-message {:type :string :headers []}))

(defmethod process-client-command ::init-edit-message [command]
  (let [message-id (:message-id command)
        collection-id (data/get-selected-collection-id)
        message (m/get-message data/messages-data collection-id message-id)]
    (swap! data/web-data assoc :edited-message message)))

(defmethod process-client-command ::select-deselect-all [command]
  (data/select-deselect-all! (:table-data command) :checked-set))

(defmethod process-client-command ::pager-action [command]
  (let [direction (:action command)
        current-page (:buffer-page @data/web-data)
        page (case direction
               :pager-fast-forward (data/total-buffer-pages)
               :pager-forward (inc current-page)
               :pager-backward (dec current-page)
               :pager-fast-backward 0)]
    (data/update-buffer-page-number! page)))



;------------------------
;gen commands

(defn add-log-entry! [message]
  (send-command! {:direction :client :command ::add-log-entry :message message}))

(defn send-init-request! []
  (send-command! {:direction :server :command ::init}))

;first init

(defn browse-queue! []
  (add-log-entry! "awaiting messages from queue")
  (send-command! {:direction     :server
                  :command       ::browse-queue
                  :connection-id (:selected-connection-id @data/web-data)
                  :queue-id      (:selected-queue-id @data/web-data)}))

(defn purge-queue []
  (send-command! {:direction     :server
                  :command       ::purge-queue
                  :connection-id (:selected-connection-id @data/web-data)
                  :queue-id      (:selected-queue-id @data/web-data)}))

(defn remove-selected-messages []
  (send-command! {:direction     :server
                  :command       ::remove-messages
                  :id-list       (:checked-collections-messages @data/web-data)
                  :collection-id (:selected-collection-id @data/web-data)
                  }))

(defn save-or-create-message []
  (send-command! {:direction     :server
                  :command       ::save-or-create-message
                  :message       (:edited-message @data/web-data)
                  :collection-id (:selected-collection-id @data/web-data)}))

(defn send-messages []
  (send-command! {:direction     :server
                  :command       ::send-messages
                  :id-list       (:checked-collections-messages @data/web-data)
                  :collection-id (:selected-collection-id @data/web-data)
                  :queue-id      (:selected-queue-id @data/web-data)
                  :connection-id (:selected-connection-id @data/web-data)}))

(defn move-buffer-to-collection []
  (send-command! {:direction     :server
                  :command       ::move-buffer-to-collection
                  :id-list       (:checked-buffer-messages @data/web-data)
                  :collection-id (:selected-collection-id @data/web-data)}))

(defn exec-client [command & params]
  (send-command! (into {:direction :client
                        :command   (keyword "ru.jms.testingtool.command" (name command))}
                       (map vec (partition 2 params)))))

