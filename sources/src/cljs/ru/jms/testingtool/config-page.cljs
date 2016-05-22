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

(defn calc-template []
  (doall (for [connection (:connections (:edited-config @data/web-data))]
           [:div.list-group-item {:key (:id connection)} (:title connection)]
           ))
  )

(defn add-connections-list [ template]
  (js-println "print connections!" template)
  [bind-fields
    [:div.list-group {:field :single-select :id :selected-edited-connection-id}
     template
     ]
    data/web-data]
  )

(defn config-page []
  [:div.container
   [:div.page-header
    [:h2 " Config "]]
   [:div.row
    [:div.container.col-md-11
     [:div.row
      [:div.container.col-md-2.nomargin
       [:div.h4 "Connections:"]
       [add-connections-list (calc-template)]
       [make-simple-button "+" "glyphicon-plus" #(let [count (count (get-in @data/web-data [:edited-config :connections]))]
                                                  (swap! data/web-data assoc-in [:edited-config :connections count] {:title "new connection"}))

        blue-button]
       ]
      [:div.col-md-1]
      [:div.container.col-md-9
       [:div.row.h4 "Details"]
       [:div.row "Title"]
       [:div.row "proeprty"]
       [:div.row [:hr]]
       [:div.row.h4 "Queues:"]
       [:div.row "queue1"]
       [:div.row "qeueu2"]
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