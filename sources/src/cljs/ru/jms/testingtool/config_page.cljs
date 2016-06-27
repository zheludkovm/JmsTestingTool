(ns ru.jms.testingtool.config-page
  (:require [reagent.core :as reagent
             :refer [atom]]
            [reagent.ratom :as ratom]
            [reagent.session :as session]
            [ru.jms.testingtool.data :as data]
            [ru.jms.testingtool.command :as comm]
            [reagent-forms.core :refer [bind-fields]]
            [ru.jms.testingtool.utils :refer [js-println make-simple-button row row4 selected-index js-is-checked indexes with-index row1 vec-remove to-zero gray-block-button blue-block-button blue-button danger-button danger-button-block switch-page! validate-func show-confirm-dialog]]
            [reagent-modals.modals :as reagent-modals]
            [ru.jms.testingtool.timer :as timer]
            [ru.jms.testingtool.shared.model :as m]
            [ru.jms.testingtool.shared.mq_providers :as mq_providers]
            [json-html.core :refer [edn->hiccup]]
            ))

(defn not-editing-connection? []
  (nil? (data/get-edited-connection-idx)))


(defn has-true? [pred coll]
  (some #{true} (map pred coll)))

(defn has-empty-field [collection field]
  (has-true? #(empty? (field %)) collection))

(defn check-field [{field :field field-type :type not-null :not-null} connection]
  (let [value (field connection)
        validate-f (validate-func field-type not-null)]
    (validate-f value)))

(defn validate-connection [connection]
  (let [provider-type (:type connection)
        provider-info (provider-type mq_providers/providers)
        queues (:queues connection)
        has-empty-title (empty? (:title connection))

        ;has-empty-queue-titles (has-empty-field queues :title)
        has-empty-queue-names (has-empty-field queues :name)
        has-incorrect-field (has-true? #(check-field % connection) (:fields provider-info))]
    (or has-empty-queue-names has-incorrect-field has-empty-title)))

(defn is-all-ok? []
  (let [edited-config (:edited-config @data/web-data)
        collection-check-result (has-empty-field (:collections edited-config) :name)
        connections-check-result (has-true? validate-connection (:connections edited-config))]
    (or collection-check-result connections-check-result)))

(defn add-collections-list []
  [:div.form-horizontal
   (for [idx (indexes (:collections (:edited-config @data/web-data)))
         :let [collection-cursor (ratom/cursor data/web-data [:edited-config :collections idx])]]
     ^{:key (str "edit" idx)}
     [bind-fields
      [:div.form-group
       [:div.col-xs-4.nomargin
        [:input.form-control {:field :input-validated :id :name :validate-func empty? :error-class "alert-danger"}]]
       [:div.col-md-5
        [make-simple-button "Remove collection" "glyphicon-minus" #(comm/exec-client :remove-collection :idx idx) danger-button]]]
      collection-cursor])])

(defn add-connections-list []
  [:div.container.col-md-2.nomargin
   [:div.h4 "Connections:"]
   [:ul.list-unstyled
    (doall (for [[idx connection] (with-index (get-in @data/web-data [:edited-config :connections]))]
             ^{:key idx}
             [:li.list-group-item
              {:on-click #(comm/exec-client :select-edited-connection :idx idx)
               :class    (if (= idx (data/get-edited-connection-idx)) "active" "")}
              [:span.disable-selection (:title connection)]
              (if (validate-connection connection)
                [:span.tomato "*"])]))]
   [make-simple-button "+" "glyphicon-plus" #(comm/exec-client :add-new-connection) blue-button]
   [make-simple-button "-" "glyphicon-minus" not-editing-connection? #(comm/exec-client :remove-selected-connection) danger-button]])

(defn add-queues-list [connection-cursor]
  [:div.form-horizontal
   [:div.form-group
    [:div.col-xs-4 "name"]
    [:div.col-xs-4 "title(optional)"]]
   (for [[idx queue] (with-index (:queues @connection-cursor))
         :let [queue-cursor (ratom/cursor connection-cursor [:queues idx])]]
     ^{:key (str "queue" idx)}
     [bind-fields
      [:div.form-group
       [:div.col-xs-4 [:input.form-control {:field :input-validated :id :name :validate-func empty? :error-class "alert-danger"}]]
       [:div.col-xs-4 [:input.form-control {:field :input-validated :id :title :validate-func empty? :error-class "grayBackground"}]]
       [:div.col-md-1 [make-simple-button "Remove property" "glyphicon-minus" #(comm/exec-client :remove-edited-queue :idx idx) danger-button]]]
      queue-cursor])
   [:div.form-group
    [:div.col-xs-8 [make-simple-button "+" "glyphicon-plus" #(comm/exec-client :add-new-queue) blue-button]]]])

(defn add-properties-list [connection-cursor]
  (let [provider-type (:type @connection-cursor)
        provider-info (provider-type mq_providers/providers)]
    [:div.form-horizontal
     [:div.form-group
      [:div.col-xs-2 "Properties:"]]
     (for [{field-name :name field :field field-type :type not-null :not-null} (:fields provider-info)]
       ^{:key (str "field" field-name)}
       [bind-fields
        [:div.form-group
         [:div.col-xs-2 field-name]
         [:div.col-xs-5 [:input.form-control {:field :input-validated :id field :validate-func (validate-func field-type not-null) :error-class "alert-danger"}]]
         [:div.col-xs-1 (name field-type)]]
        connection-cursor])]))

(defn add-edit-fields []
  (let [connection-idx (:edited-connection-idx @data/web-data)
        connection-cursor (ratom/cursor data/web-data [:edited-config :connections connection-idx])]
    ^{:key (str "connection-id=" connection-idx)}
    [:div.container.col-md-9
     [:div.row.h4 "Details"]
     [bind-fields
      [:div.form-horizontal
       [:div.row (row "Title" [:input.form-control {:field :input-validated :id :title :validate-func empty? :error-class "alert-danger"}])]
       [:div.row (row "Type" [:select.form-control {:field :list :id :type}
                              (for [[key provider] mq_providers/providers]
                                [:option {:key key} (:title provider)])])]
       [:div.row (row "Login" [:input.form-control {:field :text :id :user}])]
       [:div.row (row "Password" [:input.form-control {:field :text :id :password}])]]
      connection-cursor]
     (if (= :generic (:type @connection-cursor))
       [:div.form-horizontal
        [:div.row (row "Browse type" (name (:browse-type @connection-cursor)))]
        [:div.row (row "Info" [edn->hiccup (select-keys @connection-cursor [:class :constructor-parameters :init-calls])])]]
       (add-properties-list connection-cursor))
     [:div.row [:hr]]
     [:div.row.h4 "Queues:"]
     (add-queues-list connection-cursor)]))

(defn save-and-close []
  (comm/save-config!)
  (switch-page! :home-page)
  )

(defn config-page []
  [:div.container
   [:div.page-header
    [:h2 " Config "]
    [reagent-modals/modal-window]]
   [:div.row
    [:div.container.col-md-11
     [:div.row
      (add-connections-list)
      [:div.col-md-1]
      (if (not-editing-connection?)
        [:div.container.col-md-9
         [:div.row.h4 "Details"]]
        (add-edit-fields))]
     [:div.row
      [:hr]]
     [:div.row
      [:div.container-fluid.col-md-12
       [:div.row.h4 "Collections:"]
       [add-collections-list]
       [:div.row
        [make-simple-button "+" "glyphicon-plus" #(comm/exec-client :add-collection) blue-button]]]]]
    [:div.container.col-md-1
     [:div.row
      [make-simple-button "Ok" "glyphicon-ok" is-all-ok? #(show-confirm-dialog "Save config ?" save-and-close) danger-button-block]]
     [:div.row
      (make-simple-button "Cancel" "glyphicon-remove" #(switch-page! :home-page) blue-block-button)]]]])