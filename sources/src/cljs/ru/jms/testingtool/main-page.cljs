(ns ru.jms.testingtool.main-page
  (:require [reagent.core :as reagent
             :refer [atom]]
            [reagent.ratom :as ratom]
            [reagent.session :as session]
            [ru.jms.testingtool.data :as data]
            [ru.jms.testingtool.command :as comm]
            [reagent-forms.core :refer [bind-fields]]
            [ru.jms.testingtool.utils :refer [js-println make-simple-button row row4 selected-index js-is-checked indexes with-index row1 vec-remove to-zero validate-func
                                              switch-page! show-confirm-dialog or-property
                                              gray-block-button blue-block-button blue-button danger-button danger-button-block green-button gray-button]]
            [reagent-modals.modals :as reagent-modals]
            [ru.jms.testingtool.timer :as timer]
            [ru.jms.testingtool.shared.model :as m]
            [ru.jms.testingtool.raven :as notify]))


(defn check-queue-selection? []
  (nil? (data/get-selected-queue-id)))

(defn check-selected-collection-messages? []
  (empty? (:checked-collections-messages @data/web-data)))

(defn check-selected-collection-messages-and-queue? []
  (or (check-queue-selection?) (check-selected-collection-messages?)))

(defn check-selected-buffer-messages? []
  (empty? (:checked-buffer-messages @data/web-data)))

(defn check-pager-forward? []
  (= (:buffer-page @data/web-data) (to-zero (dec (data/total-buffer-pages)))))

(defn check-pager-backward? []
  (= (:buffer-page @data/web-data) 0))

(defn validate-header? [{type :type value :value}]
  ((validate-func type true) value))

(defn is-message-ok? []
  (let [msg (:edited-message @data/web-data)]
    (or
      (empty? (:short-title msg))
      (reduce #(or %1 (validate-header? %2) (empty? (:name %2))) false (:headers msg)))))

(defn select-message-func [id-msg checked-set]
  #(comm/exec-client :check-message :message-id id-msg :checked-set checked-set))

(defn make-message-checkbox [id-msg checked-set]
  (let [checked (contains? (checked-set @data/web-data) id-msg)]
    [:span {:class (if checked "glyphicon glyphicon-check" "glyphicon glyphicon-unchecked")}]))

(defn collections-combo [on-change-fn value-fn]
  [:select.form-control {:on-change on-change-fn
                         :value     (value-fn)}
   (doall (for [collection (data/non-buffer-collections)]
            [:option
             {:key (:id collection)}
             (:name collection)]))])

;(defn make-selection-header-checkbox [table-data]
;  [:span.glyphicon {:class    (cond
;                                (data/all-selected? table-data :checked-set) "glyphicon-check"
;                                (data/has-selected? table-data :checked-set) "glyphicon-check gray"
;                                :else "glyphicon-unchecked")
;                    :on-click #(comm/exec-client :select-deselect-all :table-data table-data)}])

(defn make-collapse-expand-button [messages extanded-set all-messages-func]
  [:span.glyphicon.btn.btn-block {:class    (cond
                                              (empty? messages) "glyphicon-zoom-out disabled btn-default"
                                              (data/has-selected? extanded-set) " btn-primary glyphicon-zoom-out"
                                              :else " btn-primary glyphicon-zoom-in")
                                  :on-click #(comm/exec-client :collapse-expand-all :all-messages-func all-messages-func :expanded-set extanded-set)}])

(defn row-collapse-button [id-msg expanded-set]
  (make-simple-button "Collapse" "glyphicon-zoom-out" #(comm/exec-client :collapse-message :message-id id-msg :expanded-set expanded-set) gray-block-button))

(defn row-expand-button [id-msg expanded-set]
  (make-simple-button "Expand" "glyphicon-zoom-in" #(comm/exec-client :expand-message :message-id id-msg :expanded-set expanded-set) gray-block-button))

(defn make-header-filter [message-column-filter]
  [bind-fields [:input.rounded {:field :text :id message-column-filter}] data/web-data])

(defn add-edit-properties []
  [:div.form-horizontal
   [:div.form-group
    [:div.col-md-3 "property"]
    [:div.col-md-3 "value"]]

   (for [idx (indexes (:headers (:edited-message @data/web-data)))
         :let [hdr-cursor (ratom/cursor data/web-data [:edited-message :headers idx])]]
     ^{:key (str "edit" idx)}
     [bind-fields
      [:div.form-horizontal
       (row4 [:input.form-control {:field :input-validated :id :name :validate-func empty? :error-class "alert-danger"}]
             [:input.form-control {:field :input-validated :id :value :validate-func #(validate-header? @hdr-cursor) :error-class "alert-danger"}]
             [:select.form-control {:field :list :id :type}
              [:option {:key :string} "string"]
              [:option {:key :long} "long"]
              [:option {:key :int} "int"]
              [:option {:key :short} "short"]
              [:option {:key :double} "double"]
              [:option {:key :float} "float"]
              [:option {:key :boolean} "boolean"]]
             [make-simple-button "Remove property" "glyphicon-minus" #(comm/exec-client :remove-message-header :idx idx)])
       ]
      hdr-cursor])])

(defn show-edit-message-dialog []
  (reagent-modals/modal!
    [:div.container
     [:div [:h1 "Edit message"]]
     [:div.form-horizontal
      [bind-fields
       [:div.form-horizontal
        (row "title" [:input.form-control {:field :input-validated :id :edited-message.short-title :validate-func empty? :error-class "alert-danger"}])
        [:br]
        (row "correlation id" [:input.form-control {:field :text :id :edited-message.jmsCorrelationId}])
        (row "priority" [:input.form-control {:field :numeric :id :edited-message.jmsPriority}])
        (row "reply to" [:input.form-control {:field :text :id :edited-message.jmsReplyTo}])]
       data/web-data]

      [:div.form-horizontal
       [:div.form-group
        [:div.col-md-2 [:label.h4 "properties :"]]]
       [add-edit-properties]
       [:div.form-group
        [:div.col-md-2 [make-simple-button "Add property" "glyphicon-plus" #(comm/exec-client :add-message-header)]]]]
      [bind-fields
       [:div.form-horizontal
        (row "body" [:textarea.form-control {:field :textarea :id :edited-message.long-title :rows "7"}])]
       data/web-data]
      [:br]
      [:div.row
       [:div.col-md-1
        [make-simple-button "Save" "glyphicon-ok" is-message-ok? #(do (comm/save-or-create-message)
                                                                      (reagent-modals/close-modal!)) blue-block-button]]
       [:div.col-md-1
        [make-simple-button "Cancel" "glyphicon-remove" #(reagent-modals/close-modal!) blue-block-button]]]
      [:br]]
     ]
    {:size :lg}))

(defn selected-different-transfer-collection? []
  (= (:transfer-collection-id @data/web-data) (:selected-collection-id @data/web-data)))

(defn show-transfer-dialog [id-msg title]
  (data/copy-current-collection-id)
  (reagent-modals/modal!
    [:div.container-fluid
     [:div [:h3 "Transfer message '" title "' to collection"]]
     [:div [:h4 ]]
     [:div [:h4 [collections-combo
                 #(comm/exec-client :select-transfer-collection :id (data/get-non-buffer-collection-id (selected-index %)))
                 data/get-transfer-collection-name]]]
     [:br]
     [:div.row
      [:div.col-md-2
       [make-simple-button "Ok" "glyphicon-ok" selected-different-transfer-collection? #(do (comm/transfer-message id-msg)
                                                                                            (reagent-modals/close-modal!)) danger-button-block]]
      [:div.col-md-2
       [make-simple-button "Cancel" "glyphicon-remove" #(reagent-modals/close-modal!) blue-block-button]]]
     [:br]]
    {:size :md}))

(defn connections-part []
  [:div.col-md-2
   [:h3 "Connections " [make-simple-button "Edit config" "glyphicon-wrench" #(do (data/prepare-config-for-edit!)
                                                                                 (switch-page! :config-page)) gray-button]]

   [:ul.list-unstyled
    (doall (for [connection (data/sorted-connections)
                 :let [connection-id (:id connection)
                       is-expanded (= (:expanded-connection-id @data/web-data) connection-id)]]
             ^{:key connection-id}
             [:div.add-margin-down
              [:span.h4 {:on-click #(comm/exec-client :expand-connection :connection-id connection-id)}
               (make-simple-button "Expand" (if is-expanded "glyphicon-zoom-out" "glyphicon-zoom-in") #() "btn btn-default btn-sm")
               " "
               [:span.disable-selection (:title connection)]]
              (if is-expanded
                [:ul.list-group
                 (doall (for [queue (data/sorted-queues connection)
                              :let [queue-id (:id queue)]]
                          ^{:key queue-id}
                          [:li.list-group-item
                           {:on-click #(comm/exec-client :select-queue :queue-id queue-id :connection-id connection-id)
                            :class    (if (= queue-id (data/get-selected-queue-id)) "active" "")}
                           [:span.disable-selection (or-property queue :title :name)]]))])]))]])

(defn gen-property
  ([label value type]
   (gen-property label value type label))
  ([label value type key]
   ^{:key key}
   [:div.form-group
    [:label.col-sm-3.control-label label]
    [:div.col-sm-5
     [:input.form-control {:type :text :disabled "disabled" :value value}]]
    (if (some? type)
      [:label.col-sm-1.control-label (name type)])]))

(defn table-row-collapsed [id-msg msg]
  (let [select-msg-props {:on-click (select-message-func id-msg :checked-collections-messages)}]
    ^{:key id-msg}
    [:tr
     [:td.col-md-1 select-msg-props [make-message-checkbox id-msg :checked-collections-messages]]
     [:td.col-md-1 select-msg-props (:short-title msg)]
     [:td.col-md-1 select-msg-props (name (:type msg))]
     [:td.col-md-1 (row-expand-button id-msg :expanded-collection-messages)]]))

(defn buffer-row-collapsed [id-msg msg]
  ^{:key id-msg}
  [:tr
   [:td.col-md-1 (:short-title msg)]
   [:td.col-md-1 (name (:type msg))]
   [:td.col-md-1 (row-expand-button id-msg :expanded-buffer-messages)]])

(defn standard-expanded-part [id-msg msg ext-property]
  [:div.container-fluid
   [:div.form-horizontal
    ext-property
    [gen-property " correlation id " (:jmsCorrelationId msg) nil]
    [gen-property " priority " (:jmsPriority msg) nil]
    [gen-property " reply to " (:jmsReplyTo msg) nil]
    (if (seq (:headers msg))
      [:div.form-group [:label.col-sm-3.control-label " properties : "]])
    (for [[idx hdr] (with-index (:headers msg))]
      ^{:key (str id-msg idx)}
      [gen-property (:name hdr) (:value hdr) (:type hdr) (str id-msg idx)])]
   [:textarea.form-control {:rows " 4 " :disabled " disabled " :value (:long-title msg)}]])

(defn collection-row-expanded [id-msg msg]
  (let [select-msg-f (select-message-func id-msg :checked-collections-messages)]
    ^{:key id-msg}
    [:tr
     [:td {:on-click select-msg-f} (make-message-checkbox id-msg :checked-collections-messages)]
     [:td {:col-span 2 :on-click select-msg-f}
      (standard-expanded-part id-msg msg [gen-property " title " (:short-title msg) nil])]
     [:td
      (row-collapse-button id-msg :expanded-collection-messages)
      (make-simple-button "Edit" "glyphicon-wrench" #(do (comm/exec-client :init-edit-message :message-id id-msg) (show-edit-message-dialog)) gray-block-button)
      (make-simple-button "Transfer" "glyphicon-transfer" #(show-transfer-dialog id-msg (:short-title msg)) gray-block-button)
      (make-simple-button "Remove" "glyphicon-trash" (fn [] (show-confirm-dialog "Remove selected messages?" #(comm/remove-selected-messages id-msg))) danger-button-block)]]))

(defn buffer-row-expanded [id-msg msg]
  ^{:key id-msg}
  [:tr
   [:td {:col-span 2}
    (standard-expanded-part id-msg msg [gen-property " jms message id " (:jmsMessageId msg) nil])]
   [:td
    (row-collapse-button id-msg :expanded-buffer-messages)
    (make-simple-button "To collection" "glyphicon-download-alt" #(comm/move-buffer-to-collection id-msg) gray-block-button)]])

(defn show-pager []
  [:div.row
   [:div.col-xs-4.col-xs-offset-3
    [:div.button-group
     [make-simple-button "to start" "glyphicon-fast-backward" check-pager-backward? #(comm/exec-client :pager-action :action :pager-fast-backward) blue-button]
     [make-simple-button "one page left" "glyphicon-backward" check-pager-backward? #(comm/exec-client :pager-action :action :pager-backward) blue-button]]]
   [:div.col-xs-1
    [:h4 (str (inc (:buffer-page @data/web-data)) "/" (data/total-buffer-pages))]]
   [:div.col-xs-4
    [:div.button-group.right-group
     [make-simple-button "one page right" "glyphicon-forward" check-pager-forward? #(comm/exec-client :pager-action :action :pager-forward) blue-button]
     [make-simple-button "to end" "glyphicon-fast-forward" check-pager-forward? #(comm/exec-client :pager-action :action :pager-fast-forward) blue-button]]]])

(def buffer-buttons
  [:div.col-md-1.column-auto
   [make-simple-button "Get" "glyphicon-refresh" check-queue-selection? comm/browse-queue! "btn btn-primary middle-button"]
   [:br]
   [make-simple-button "Clean queue" "glyphicon-trash" check-queue-selection? #(show-confirm-dialog "Clean message queue?" comm/purge-queue) danger-button]])

(def collection-buttons
  [:div.col-sm-1.column-auto
   [make-simple-button "Send message to queue!" "glyphicon-flash" check-selected-collection-messages-and-queue? #(do
                                                                                                                  (comm/send-messages)
                                                                                                                  (comm/exec-client :add-log-entry :message "Send message!" :level :warning)
                                                                                                                  ) green-button]
   [:br]
   [make-simple-button "Add new message" "glyphicon-plus" #(do (comm/exec-client :init-add-message)
                                                               (show-edit-message-dialog))]])

(defn messages-list [expanded-set messages render-collapsed-row render-expanded-row]
  (let [expanded-messages (expanded-set @data/web-data)]
    (doall (for [msg messages
                 :let [id-msg (:id msg)]]
             (if (not (contains? expanded-messages id-msg))
               (render-collapsed-row id-msg msg)
               (render-expanded-row id-msg msg))))))

(defn collection-table-part []
  (let [messages (data/filtered-collection-messages)]
    [:div.container-fluid
     [:div.row
      [:div.col-sm-1]
      [:div.col-xs-6 [:h2 "Collection messages"]]
      [:div.col-xs-4 [:br] (collections-combo
                             #(comm/exec-client :select-collection :id (data/get-non-buffer-collection-id (selected-index %)))
                             data/get-selected-collection-name)]]
     [:div.row
      collection-buttons
      [:div.col-md-11
       [:table.table
        [:thead
         [:tr
          [:th.col-md-1]
          [:th.col-md-7 [:div [:span.add-margin-right "title"] [:span (make-header-filter :collection-filter)]]]
          [:th.col-md-1 " type "]
          [:th.col-md-1 (make-collapse-expand-button messages :expanded-collection-messages data/filtered-collection-messages)]]]
        [:tbody
         (messages-list :expanded-collection-messages messages table-row-collapsed collection-row-expanded)]]]]]))

(defn buffer-table-part []
  (let [messages (data/paged-buffer-messages)]
    [:div.container-fluid
     [:div.row
      [:div.col-sm-1]
      [:div.col-xs-6 [:h2 "Buffer"]]
      [:div.col-xs-4 [:br] (if (> (data/total-buffer-pages) 1) (show-pager))]]
     [:div.row
      buffer-buttons
      [:div.col-md-11
       [:table.table
        [:thead
         [:tr
          [:th.col-md-8 [:div [:span.add-margin-right "message"] [:span (make-header-filter :buffer-filter)]]]
          [:th.col-md-1 " type "]
          [:th.col-md-1 (make-collapse-expand-button messages :expanded-buffer-messages data/filtered-buffer-messages)]]]
        [:tbody
         (messages-list :expanded-buffer-messages messages buffer-row-collapsed buffer-row-expanded)]]]]]))

;(defn log-part []
;  [:div.col-md-20
;   [:h3.text-right " Log "]
;   [:ul.list-unstyled
;    (doall (for [item (:log-entries @data/web-data)]
;             (do ^{:key (.getTime (:time item))}
;                 [:li.text-right.alert.alert-info {:class (:level item)}
;                  [:small (str (:time item))]
;                  [:span " "]
;                  [:strong (:text item)]])))]])

(defn home-page []
  [:div.container-fluid.root-container
   [:div.page-header
    [:h1 " JMS Testing tool "]
    [reagent-modals/modal-window]
    [notify/notifications]]
   [:div.row
    (connections-part)
    [:div.container.col-md-10
     [:div.row (buffer-table-part)]
     [:div.row [:hr]]
     [:div.row (collection-table-part)]]]
   [:div.page-footer
    [:div
     ;(log-part)
     ;[:a {:href " #/about "} " about "]
     ]
    ]
   ])

(js-println " main page initialized! ")