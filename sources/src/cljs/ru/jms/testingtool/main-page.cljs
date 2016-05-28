(ns ru.jms.testingtool.main-page
  (:require [reagent.core :as reagent
             :refer [atom]]
            [reagent.ratom :as ratom]
            [reagent.session :as session]
            [ru.jms.testingtool.data :as data]
            [ru.jms.testingtool.command :as comm]
            [reagent-forms.core :refer [bind-fields]]
            [ru.jms.testingtool.utils :refer [js-println make-simple-button row row4 selected-index js-is-checked indexes with-index row1 vec-remove to-zero validate-func
                                              switch-page! show-confirm-dialog
                                              gray-block-button blue-block-button blue-button danger-button danger-button-block]]
            [reagent-modals.modals :as reagent-modals]
            [ru.jms.testingtool.timer :as timer]
            [ru.jms.testingtool.shared.model :as m]))


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

(defn select-message-func [id-msg table-info]
  #(comm/exec-client :check-message :message-id id-msg :checked-set (:checked-set table-info)))

(defn make-message-checkbox [id-msg table-data]
  (let [checked-set (:checked-set table-data)
        checked (contains? (checked-set @data/web-data) id-msg)]
    [:span {:class (if checked "glyphicon glyphicon-check" "glyphicon glyphicon-unchecked")}]))

(defn collections-combo []
  [:select.form-control {:on-change #(comm/exec-client :select-collection :id (data/get-non-buffer-collection-id (selected-index %)))
                         :value     (data/get-selected-collection-name)}
   (doall (for [collection (data/non-buffer-collections)]
            [:option
             {:key (:id collection)}
             (:name collection)]))])

(defn make-selection-header-checkbox [table-data]
  [:span.glyphicon {:class    (cond
                                (data/all-selected? table-data :checked-set) "glyphicon-check"
                                (data/has-selected? table-data :checked-set) "glyphicon-check gray"
                                :else "glyphicon-unchecked")
                    :on-click #(comm/exec-client :select-deselect-all :table-data table-data)}])

(defn make-collapse-expand-button [messages table-data]
  [:span.glyphicon.btn.btn-block {:class    (cond
                                              (empty? messages) "glyphicon-zoom-out disabled btn-default"
                                              (data/has-selected? table-data :expanded-set) " btn-primary glyphicon-zoom-out"
                                              :else " btn-primary glyphicon-zoom-in")
                                  :on-click #(comm/exec-client :collapse-expand-all :table-data table-data)}])

(defn make-header-filter [table-data]
  [bind-fields [:input.rounded {:field :text :id (:message-column-filter table-data)}] data/web-data])

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


(defn connections-part []
  [:div.col-md-2 [:h3 "Connections"]
   [:ul.list-unstyled
    (doall (for [connection (:connections @data/config-data)
                 :let [connection-id (:id connection)]]
             ^{:key connection-id}
             [:li
              (:title connection)
              [:ul.list-group
               (doall (for [queue (:queues connection)
                            :let [queue-id (:id queue)]]
                        ^{:key queue-id}
                        [:li.list-group-item
                         {:on-click #(comm/exec-client :select-queue :queue-id queue-id :connection-id connection-id)
                          :class    (if (= queue-id (data/get-selected-queue-id)) "active" "")}
                         [:span.disable-selection (:title queue)]]))]]))]

   [make-simple-button "Edit config" "glyphicon-wrench" #(do (data/prepare-config-for-edit!)
                                                             (switch-page! :config-page)) blue-button]
   ])

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

(defn table-row-collapsed [id-msg msg table-data]
  (let [select-msg-props {:on-click (select-message-func id-msg table-data)}]
    ^{:key id-msg}
    [:tr
     [:td.col-md-1 select-msg-props [make-message-checkbox id-msg table-data]]
     [:td.col-md-1 select-msg-props (:short-title msg)]
     [:td.col-md-1 select-msg-props (name (:type msg))]
     [:td.col-md-1 (make-simple-button "Expand"
                                       "glyphicon-zoom-in"
                                       #(comm/exec-client :expand-message :message-id id-msg :expanded-set (:expanded-set table-data))
                                       gray-block-button)]]))

(defn table-row-expanded [id-msg msg table-data]
  (let [select-msg-f (select-message-func id-msg table-data)]
    ^{:key id-msg}
    [:tr
     [:td {:on-click select-msg-f} [make-message-checkbox id-msg table-data]]
     [:td {:col-span 2 :on-click select-msg-f}
      [:div.container-fluid
       [:div.form-horizontal
        (if-not (:is-editable table-data)
          [gen-property " jms message id " (:jmsMessageId msg) nil]
          [gen-property " title " (:short-title msg) nil])
        [gen-property " correlation id " (:jmsCorrelationId msg) nil]
        [gen-property " priority " (:jmsPriority msg) nil]
        [gen-property " reply to " (:jmsReplyTo msg) nil]
        ;gen headers part
        (if (seq (:headers msg))
          [:div.form-group [:label.col-sm-3.control-label " properties : "]])
        (for [[idx hdr] (with-index (:headers msg))]
          ^{:key (str id-msg idx)}
          [gen-property (:name hdr) (:value hdr) (:type hdr) (str id-msg idx)])]
       ;end headers part
       [:textarea.form-control {:rows " 4 " :disabled " disabled " :value (:long-title msg)}]]]
     [:td
      [make-simple-button
       "Collapse" "glyphicon-zoom-out"
       #(comm/exec-client :collapse-message :message-id id-msg :expanded-set (:expanded-set table-data))
       gray-block-button]
      (if (:is-editable table-data)
        [make-simple-button
         "Edit" "glyphicon-wrench"
         #(do (comm/exec-client :init-edit-message :message-id id-msg) (show-edit-message-dialog))
         gray-block-button])]]))

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
  [:div.col-md-1
   [make-simple-button "Get" "glyphicon-refresh" check-queue-selection? comm/browse-queue! blue-button]
   [:br]
   [:br]
   [make-simple-button "To collection" "glyphicon-download-alt" check-selected-buffer-messages? comm/move-buffer-to-collection blue-button]
   [make-simple-button "Clean queue" "glyphicon-trash" check-queue-selection? #(show-confirm-dialog "Clean message queue?" comm/purge-queue) danger-button]
   ])

(def collection-buttons
  [:div.col-md-1
   [make-simple-button "Put to queue" "glyphicon-arrow-left" check-selected-collection-messages-and-queue? comm/send-messages blue-button]
   [make-simple-button "Add new message" "glyphicon-plus" #(do (comm/exec-client :init-add-message)
                                                               (show-edit-message-dialog))]
   [make-simple-button "Remove" "glyphicon-minus" check-selected-collection-messages? #(show-confirm-dialog "Remove selected messages?" comm/remove-selected-messages) danger-button]])

(def buffer-table {
                   :messages-func         data/paged-buffer-messages
                   :all-messages-func     data/filtered-buffer-messages
                   :header                "Buffer"
                   :show-collection-combo false
                   :buttons               buffer-buttons
                   :message-column-header "message"
                   :checked-set           :checked-buffer-messages
                   :message-column-filter :buffer-filter
                   :expanded-set          :expanded-buffer-messages
                   :is-editable           false
                   :show-pager            true})

(def collection-table {
                       :messages-func         data/filtered-collection-messages
                       :all-messages-func     data/filtered-collection-messages
                       :header                "Collection messages"
                       :show-collection-combo true
                       :buttons               collection-buttons
                       :message-column-header "title"
                       :checked-set           :checked-collections-messages
                       :message-column-filter :collection-filter
                       :expanded-set          :expanded-collection-messages
                       :is-editable           true
                       :show-pager            false})

(defn messages-table-part [table-data]
  (let [expanded-messages ((:expanded-set table-data) @data/web-data)
        messages ((:messages-func table-data))]
    [:div.container-fluid
     [:div.row
      [:div.col-xs-1]
      [:div.col-xs-6
       [:h2 (:header table-data)]]
      [:div.col-xs-5
       [:br]
       (if (:show-collection-combo table-data) (collections-combo))
       (if (and (:show-pager table-data) (> (data/total-buffer-pages) 1)) (show-pager))]]
     [:div.row
      (:buttons table-data)
      [:div.col-md-11
       [:table.table
        [:thead
         [:tr
          [:th.col-md-1 (make-selection-header-checkbox table-data)]
          [:th.col-md-7 [:div [:span.add-margin-right (:message-column-header table-data)] [:span (make-header-filter table-data)]]]
          [:th.col-md-1 " type "]
          [:th.col-md-1 (make-collapse-expand-button messages table-data)]]]
        [:tbody
         (doall (for [msg messages
                      :let [id-msg (:id msg)]]
                  (if (not (contains? expanded-messages id-msg))
                    (table-row-collapsed id-msg msg table-data)
                    (table-row-expanded id-msg msg table-data)
                    )))]]]]]))

(defn log-part []
  [:div.col-md-20
   [:h3.text-right " Log "]
   [:ul.list-unstyled
    (doall (for [item (:log-entries @data/web-data)]
             (do ^{:key (.getTime (:time item))}
                 [:li.text-right.alert.alert-info {:class (:level item)}
                  [:small (str (:time item))]
                  [:span " "]
                  [:strong (:text item)]])))]])

(defn home-page []
  [:div.container
   [:div.page-header
    [:h1 " JMS Testing tool "]
    [reagent-modals/modal-window]]
   [:div.row
    (connections-part)
    [:div.container.col-md-10
     [:div.row (messages-table-part buffer-table)]
     [:div.row [:hr]]
     [:div.row (messages-table-part collection-table)]]]
   [:div.page-footer
    [:div
     (log-part)
     ;[:a {:href " #/about "} " about "]
     ]]])

(js-println " main page initialized! ")