(ns ru.jms.testingtool.config-page
  (:require [reagent.core :as reagent
             :refer [atom]]
            [reagent.ratom :as ratom]
            [reagent.session :as session]
            [ru.jms.testingtool.data :as data]
            [ru.jms.testingtool.command :as comm]
            [reagent-forms.core :refer [bind-fields]]
            [ru.jms.testingtool.utils :refer [js-println make-simple-button row row4 selected-index js-is-checked indexes with-index row1 vec-remove to-zero gray-block-button blue-block-button blue-button danger-button danger-button-block switch-page!]]
            [reagent-modals.modals :as reagent-modals]
            [ru.jms.testingtool.timer :as timer]
            [ru.jms.testingtool.shared.model :as m]
            [ru.jms.testingtool.shared.mq_providers :as mq_providers]
            [json-html.core :refer [edn->hiccup]]
            ))

(defn not-editing-connection? []
  (nil? (data/get-edited-connection-idx)))

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
        [make-simple-button "Remove property" "glyphicon-minus" #(comm/exec-client :remove-collection :idx idx) danger-button]]]
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
              [:span.disable-selection (:title connection)]]))]
   [make-simple-button "+" "glyphicon-plus" #(comm/exec-client :add-new-connection) blue-button]
   [make-simple-button "-" "glyphicon-minus" not-editing-connection? #(comm/exec-client :remove-selected-connection) danger-button]
   ]
  )

(defn add-queues-list [connection-cursor]
  [:div.form-horizontal
   [:div.form-group
    [:div.col-xs-4 "name"]
    [:div.col-xs-4 "title"]]
   (for [[idx queue] (with-index (:queues @connection-cursor))
         :let [queue-cursor (ratom/cursor connection-cursor [:queues idx])]]
     ^{:key (str "queue" idx)}
     [bind-fields
      [:div.form-group
       [:div.col-xs-4 [:input.form-control {:field :input-validated :id :name :validate-func empty? :error-class "alert-danger"}]]
       [:div.col-xs-4 [:input.form-control {:field :input-validated :id :title :validate-func empty? :error-class "alert-danger"}]]
       [:div.col-md-1 [make-simple-button "Remove property" "glyphicon-minus" #(comm/exec-client :remove-edited-queue :idx idx) danger-button]]]
      queue-cursor])
   [:div.form-group
    [:div.col-xs-8 [make-simple-button "+" "glyphicon-plus" #(comm/exec-client :add-new-queue) blue-button]]]
   ])

(defn add-properties-list [connection-cursor]
  (let [provider-type (:type @connection-cursor)
        provider-info (provider-type mq_providers/providers)]
    [:div.form-horizontal
     [:div.form-group
      [:div.col-xs-2 "property"]
      [:div.col-xs-5 "value"]
      [:div.col-xs-1 "type"]
      ]
     (for [[idx {field-name :name field :field field-type :type not-null :not-null}] (with-index (:fields provider-info))
           :let [field-cursor (ratom/cursor connection-cursor [:queues field])]]
       ^{:key (str "field" idx)}
       [bind-fields
        [:div.form-group
         [:div.col-xs-2 field-name]
         [:div.col-xs-5 [:input.form-control {:field :input-validated :id :value :validate-func empty? :error-class "alert-danger"}]]
         [:div.col-xs-1 (name field-type)]]
        field-cursor])
     ]))

(defn add-edit-fields []
  (let [connection-idx (:edited-connection-idx @data/web-data)
        connection-cursor (ratom/cursor data/web-data [:edited-config :connections connection-idx])
        ]
    ^{:key (str "connection-id=" connection-idx)}
    [:div.container.col-md-9
     [:div.row.h4 "Details"]
     [bind-fields
      [:div.form-horizontal
       [:div.row (row "Title" [:input.form-control {:field :text :id :title}])]
       [:div.row (row "Type" [:select.form-control {:field :list :id :type}
                              (for [[key provider] mq_providers/providers]
                                [:option {:key key} (:title provider)])])]
       ]
      connection-cursor]
     (if (= :generic (:type @connection-cursor))
       [:div.form-horizontal
        [:div.row (row "Info" [edn->hiccup (select-keys @connection-cursor [:class :constructor-parameters :init-calls])])]]

       (add-properties-list connection-cursor)
       )
     [:div.row [:hr]]
     [:div.row.h4 "Queues:"]
     (add-queues-list connection-cursor)
     ;[:div.row "queue1"]
     ;[:div.row "qeueu2"]
     ]
    )
  )

(defn config-page []
  [:div.container
   [:div.page-header
    [:h2 " Config "]]
   [:div.row
    [:div.container.col-md-11
     [:div.row
      (add-connections-list)
      [:div.col-md-1]
      (if (not-editing-connection?)
        [:div.container.col-md-9
         [:div.row.h4 "Details"]]
        (add-edit-fields)
        )
      ]
     [:div.row
      [:hr]]
     [:div.row
      [:div.container-fluid.col-md-12
       [:div.row.h4 "Collections:"]
       [add-collections-list]
       [:div.row
        [make-simple-button "+" "glyphicon-plus" #(comm/exec-client :add-collection) blue-button]]
       ]
      ]
     ]

    [:div.container.col-md-1
     [:div.row
      [make-simple-button "Ok" "glyphicon-ok" #(js-println "Ok!") danger-button]]
     [:div.row
      [make-simple-button "Cancel" "glyphicon-remove" #(js-println "cancel!") blue-button]]
     ]
    ]

   ])