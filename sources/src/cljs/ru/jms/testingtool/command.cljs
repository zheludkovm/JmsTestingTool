(ns ru.jms.testingtool.command
  (:require [ru.jms.testingtool.utils :refer [js-println xor-assoc vec-remove gen-id]]
            [ru.jms.testingtool.data :as data]
            [ru.jms.testingtool.dispatcher :refer [send-command! process-client-command]]
            [ru.jms.testingtool.shared.model :as m]))

(declare browse-queue!)
(declare add-log-entry!)
;--------------------
;process server commands

(defmethod process-client-command ::init [{config :config}]
  (reset! data/config-data (m/map->ConfigType config))
  (swap! data/web-data assoc
         :selected-collection-id (m/get-first-collection-id @data/config-data)
         :selected-connection-id nil
         :selected-queue-id nil)
  (m/init-messages! data/messages-data :buffer [])
  (js-println data/messages-data))

(defmethod process-client-command ::new-message [{msg :msg collection-id :collection-id}]
  (m/add-message! data/messages-data collection-id msg))

(defmethod process-client-command ::init-messages [{messages :messages collection-id :collection-id}]
  (m/init-messages! data/messages-data collection-id messages)
  (data/validate-current-page-number!)
  (if (= collection-id :buffer)
    (swap! data/web-data assoc :checked-buffer-messages #{}))
  (add-log-entry! (str "receive " (count messages) " messages into collection " (data/get-collection-name collection-id))))

(defmethod process-client-command ::remove-messages [{id-list :id-list collection-id :collection-id}]
  (m/remove-messages! data/messages-data collection-id id-list)
  (swap! data/web-data assoc :checked-collections-messages #{}))

;client commands

(defmethod process-client-command ::select-queue [{queue-id :queue-id connection-id :connection-id}]
  (xor-assoc data/web-data :selected-queue-id queue-id)
  (swap! data/web-data assoc :selected-connection-id connection-id)
  (m/init-messages! data/messages-data :buffer [])
  (swap! data/web-data assoc :expanded-buffer-messages #{})
  (swap! data/web-data assoc :checked-buffer-messages #{})
  (if (some? (:selected-queue-id @data/web-data))
    (browse-queue!)))

(defmethod process-client-command ::add-log-entry [{message :message level :level}]
  (let [log-entries (:log-entries @data/web-data)
        updated-log-entries (conj log-entries {:time (js/Date.) :text message :level level})]
    (swap! data/web-data assoc :log-entries updated-log-entries)))

(defmethod process-client-command ::select-collection [{id :id}]
  (data/select-collection! id))

(defmethod process-client-command ::remove-message-header [{idx :idx}]
  (let [headers (get-in @data/web-data [:edited-message :headers])
        changed-headers (vec-remove headers idx)]
    (swap! data/web-data assoc-in [:edited-message :headers] changed-headers)))

(defmethod process-client-command ::add-message-header [_]
  (let [count (count (get-in @data/web-data [:edited-message :headers]))]
    (swap! data/web-data assoc-in [:edited-message :headers count] {})))

(defmethod process-client-command ::expand-message [{message-id :message-id expanded-set :expanded-set}]
  (swap! data/web-data update-in [expanded-set] conj message-id))

(defmethod process-client-command ::collapse-message [{message-id :message-id expanded-set :expanded-set}]
  (swap! data/web-data update-in [expanded-set] disj message-id))

(defmethod process-client-command ::collapse-expand-all [{table-data :table-data}]
  (data/select-deselect-all! table-data :expanded-set))

(defmethod process-client-command ::check-message [{message-id :message-id checked-set-symb :checked-set}]
  (let [checked-set (checked-set-symb @data/web-data)
        is-checked (not (contains? checked-set message-id))
        op (if is-checked conj disj)
        new-checked-set (op checked-set message-id)]
    (swap! data/web-data assoc checked-set-symb new-checked-set)))

(defmethod process-client-command ::init-add-message [_]
  (swap! data/web-data assoc :edited-message {:type :string :headers []}))

(defmethod process-client-command ::init-edit-message [{message-id :message-id}]
  (let [collection-id (data/get-selected-collection-id)
        message (m/get-message data/messages-data collection-id message-id)]
    (swap! data/web-data assoc :edited-message message)))

(defmethod process-client-command ::select-deselect-all [{table-data :table-data}]
  (data/select-deselect-all! table-data :checked-set))

(defmethod process-client-command ::pager-action [{action :action}]
  (let [current-page (:buffer-page @data/web-data)
        page (case action
               :pager-fast-forward (data/total-buffer-pages)
               :pager-forward (inc current-page)
               :pager-backward (dec current-page)
               :pager-fast-backward 0)]
    (data/update-buffer-page-number! page)))

;edit config commands
(defmethod process-client-command ::select-edited-connection [{idx :idx}]
  (swap! data/web-data assoc :edited-connection-idx idx))

(defmethod process-client-command ::add-new-connection [_]
  (let [count (count (get-in @data/web-data [:edited-config :connections]))]
    (swap! data/web-data assoc-in [:edited-config :connections count] {:id (gen-id) :title "new connection"})))

(defmethod process-client-command ::remove-selected-connection []
  (let [idx (:edited-connection-idx @data/web-data)
        filtered-connections (vec-remove (get-in @data/web-data [:edited-config :connections]) idx)]
    (swap! data/web-data assoc-in [:edited-config :connections] filtered-connections)
    (swap! data/web-data assoc-in [:edited-connection-idx] nil)))

(defmethod process-client-command ::add-new-queue [_]
  (let [idx (:edited-connection-idx @data/web-data)
        count (count (get-in @data/web-data [:edited-config :connections idx :queues]))]
    (swap! data/web-data assoc-in [:edited-config :connections idx :queues count] {:id (gen-id) :title "new queue" :name "new queue"})))

(defmethod process-client-command ::remove-edited-queue [{idx :idx}]
  (let [connection-idx (:edited-connection-idx @data/web-data)
        queues (get-in @data/web-data [:edited-config :connections connection-idx :queues])
        filtered-queues (vec-remove queues idx)]
    (swap! data/web-data assoc-in [:edited-config :connections connection-idx :queues] filtered-queues)))



(defmethod process-client-command ::add-collection [_]
  (let [count (count (get-in @data/web-data [:edited-config :collections]))]
    (swap! data/web-data assoc-in [:edited-config :collections count] {:id (gen-id) :title "new collection"})))

(defmethod process-client-command ::remove-collection [{idx :idx}]
  (let [collections (get-in @data/web-data [:edited-config :collections])
        filtered-collections (vec-remove collections idx)]
    (swap! data/web-data assoc-in [:edited-config :collections] filtered-collections)))





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

