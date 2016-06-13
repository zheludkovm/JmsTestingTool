(ns ru.jms.testingtool.command
  (:require [ru.jms.testingtool.utils :refer [js-println xor-assoc vec-remove gen-id or-property or-property vec-sort-by f-vec-remove f-conj swap-transform!] :as u]
            [ru.jms.testingtool.data :as data]
            [ru.jms.testingtool.dispatcher :refer [send-command! process-client-command]]
            [ru.jms.testingtool.shared.model :as m]
            [com.rpl.specter :as s]
            [ru.jms.testingtool.utils.raven :as notify]
            ))

(declare browse-queue!)
(declare add-log-entry!)
;--------------------
;process server commands

(defn add-notify [message level]
  (notify/notify message :type level :delay 2000))

(defmethod process-client-command ::init [{config :config}]
  (reset! data/config-data (m/map->ConfigType config))
  (swap! data/web-data assoc
         :selected-collection-id (m/get-first-collection-id @data/config-data)
         :expanded-connection-id (:id (first (data/sorted-connections)))
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
    (add-notify (str "receive " (count messages) " messages") :info)))

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
  (add-notify message level))

(defmethod process-client-command ::select-collection [{id :id}]
  (data/select-collection! id))

(defmethod process-client-command ::select-transfer-collection [{id :id}]
  (swap! data/web-data assoc :transfer-collection-id id))

(defmethod process-client-command ::remove-message-header [{idx :idx}]
  (u/swap-transform! data/web-data [:edited-message :headers] (u/f-vec-remove idx)))

(defmethod process-client-command ::add-message-header [_]
  (u/swap-transform! data/web-data [:edited-message :headers] (u/f-conj {})))

(defmethod process-client-command ::expand-message [{message-id :message-id expanded-set :expanded-set}]
  (swap! data/web-data update-in [expanded-set] conj message-id))

(defmethod process-client-command ::collapse-message [{message-id :message-id expanded-set :expanded-set}]
  (swap! data/web-data update-in [expanded-set] disj message-id))

(defmethod process-client-command ::collapse-expand-all [{all-messages-func :all-messages-func expanded-set :expanded-set}]
  (data/select-deselect-all! all-messages-func expanded-set))

(defmethod process-client-command ::check-message [{message-id :message-id checked-set-symb :checked-set}]
  (let [checked-set (checked-set-symb @data/web-data)
        is-checked (not (contains? checked-set message-id))
        new-checked-set (if is-checked #{message-id} #{})]
    (swap! data/web-data assoc checked-set-symb new-checked-set)))

(defmethod process-client-command ::init-add-message [_]
  (swap! data/web-data assoc :edited-message {:type :string :headers []}))

(defmethod process-client-command ::init-edit-message [{message-id :message-id}]
  (let [collection-id (data/get-selected-collection-id)
        message (m/get-message data/messages-data collection-id message-id)]
    (swap! data/web-data assoc :edited-message message)))

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
  (let [new-connection {:id (u/gen-id) :type :activemq :title "new connection" :browse-type :browser :queues []}]
    (u/swap-transform! data/web-data [:edited-config :connections] (u/f-conj new-connection))))

(defmethod process-client-command ::remove-selected-connection []
  (let [connection-idx (:edited-connection-idx @data/web-data)]
    (u/swap-transform! data/web-data [:edited-config :connections] (u/f-vec-remove connection-idx))
    (swap! data/web-data assoc-in [:edited-connection-idx] nil)))

(defmethod process-client-command ::add-new-queue [_]
  (let [connection-idx (:edited-connection-idx @data/web-data)]
    (u/swap-transform! data/web-data [:edited-config :connections (s/keypath connection-idx) :queues] (u/f-conj {:id (u/gen-id) :name "new queue"}))))

(defmethod process-client-command ::remove-edited-queue [{idx :idx}]
  (let [connection-idx (:edited-connection-idx @data/web-data)]
    (u/swap-transform! data/web-data [:edited-config :connections (s/keypath connection-idx) :queues] (u/f-vec-remove idx))))

(defmethod process-client-command ::add-collection [_]
  (let [new-collection {:id (u/gen-id) :name "new collection"}]
    (u/swap-transform! data/web-data [:edited-config :collections] (u/f-conj new-collection))))

(defmethod process-client-command ::remove-collection [{idx :idx}]
  (u/swap-transform! data/web-data [:edited-config :collections] (u/f-vec-remove idx)))

(defmethod process-client-command ::expand-connection [{connection-id :connection-id}]
  (swap! data/web-data assoc
         :selected-connection-id nil
         :selected-queue-id nil)
  (xor-assoc data/web-data :expanded-connection-id connection-id))

;------------------------
;gen commands

(defn add-log-entry! [message]
  (send-command! {:direction :client :command ::add-log-entry :message message}))

(defn send-init-request! []
  (send-command! {:direction :server :command ::init}))

;first init

(defn browse-queue! []
  (send-command! {:direction     :server
                  :command       ::browse-queue
                  :connection-id (:selected-connection-id @data/web-data)
                  :queue-id      (:selected-queue-id @data/web-data)}))

(defn purge-queue []
  (send-command! {:direction     :server
                  :command       ::purge-queue
                  :connection-id (:selected-connection-id @data/web-data)
                  :queue-id      (:selected-queue-id @data/web-data)}))

(defn remove-selected-messages [id-msg]
  (send-command! {:direction     :server
                  :command       ::remove-messages
                  :id-list       #{id-msg}
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

(defn move-buffer-to-collection [id-msg]
  (send-command! {:direction     :server
                  :command       ::move-buffer-to-collection
                  :id-list       #{id-msg}
                  :collection-id (:selected-collection-id @data/web-data)}))


(defn save-config! []
  (send-command! {:direction :server
                  :command   ::save-config
                  :config    (->> (:edited-config @data/web-data)
                                  (s/transform [:connections s/ALL :queues] (fn [coll] (u/vec-sort-by #(u/or-property % :title :name) coll)))
                                  (s/transform [:connections] #(u/vec-sort-by :title %))
                                  )}))

(defn exec-client [command & params]
  (send-command! (into {:direction :client
                        :command   (keyword "ru.jms.testingtool.command" (name command))}
                       (map vec (partition 2 params)))))

(defn transfer-message [id-msg]
  (send-command! {:direction          :server
                  :command            ::transfer-message-to-collection
                  :id-msg             id-msg
                  :from-collection-id (:selected-collection-id @data/web-data)
                  :to-collection-id   (:transfer-collection-id @data/web-data)}))
