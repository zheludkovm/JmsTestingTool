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
            [ru.jms.testingtool.shared.model :as m]))

(defn has-edited-connection []
  (nil? (data/get-edited-connection-id)))

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
        [make-simple-button "Remove property" "glyphicon-minus" #(comm/exec-client :remove-message-header :idx idx) danger-button]]]
      collection-cursor])])


(defn add-connections-list []
  [:div.container.col-md-2.nomargin
   [:div.h4 "Connections:"]
   [:ul.list-unstyled
    (doall (for [connection (get-in @data/web-data [:edited-config :connections])
                 :let [connection-id (:id connection)]]
             ^{:key connection-id}
             [:li.list-group-item
              {:on-click #(comm/exec-client :select-edited-connection :connection-id connection-id)
               :class    (if (= connection-id (data/get-edited-connection-id)) "active" "")}
              [:span.disable-selection (:title connection)]]))]
   [make-simple-button "+" "glyphicon-plus" #(comm/exec-client :add-new-connection) blue-button]
   [make-simple-button "-" "glyphicon-minus" has-edited-connection #(comm/exec-client :remove-selected-connection) danger-button]
   ]
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
      [:div.container.col-md-9
       [:div.row.h4 "Details"]
       (if (has-edited-connection)
         [[:div.row "Title"]
          [:div.row "proeprty"]
          [:div.row [:hr]]
          [:div.row.h4 "Queues:"]
          [:div.row "queue1"]
          [:div.row "qeueu2"]])
       ]]
     [:div.row
      [:hr]]
     [:div.row
      [:div.container-fluid.col-md-12
       [:div.row.h4 "Collections:"]
       [add-collections-list]
       [:div.row
        [make-simple-button "+" "glyphicon-plus" #(js-println "Ok!") blue-button]]
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