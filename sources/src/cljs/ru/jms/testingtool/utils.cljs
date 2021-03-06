(ns ru.jms.testingtool.utils
  (:require-macros [reagent-forms.macros :refer [render-element]])
  (:require [reagent-forms.core :as forms-core]
            [clojure.string :as str]
            [reagent.session :as session]
            [reagent-modals.modals :as reagent-modals]
            [com.rpl.specter :as s]))

(def gray-block-button "btn btn-default btn-block")
(def gray-button "btn btn-default")
(def blue-block-button "btn btn-primary btn-block")
(def blue-button "btn btn-primary")
(def green-button "btn high-button btn-success")
(def danger-button "btn btn-danger")
(def danger-button-block "btn btn-danger btn-block")

(defn xor-assoc [data key value]
  (swap! data assoc key (if (= (get @data key) value) nil value)))

(defn js-println [& msg]
  (.log js/console (apply str msg)))

(defn make-simple-button
  ([text glyph disabled-function on-click add-class]
   [:button {:type     "button"
             :class    add-class
             :title    text
             :value    text
             :disabled (disabled-function)
             :on-click on-click}
    (if (some? glyph)
      [:span {:class (str "glyphicon " glyph)}])])
  ([text glyph on-click add-class]
   (make-simple-button text glyph (constantly false) on-click add-class))
  ([text glyph on-click]
   (make-simple-button text glyph (constantly false) on-click "btn btn-primary")))

(defn row1 [input]
  [:div.form-group
   [:div.col-md-9 input]])

(defn row [label input]
  [:div.form-group
   [:div.col-md-2 [:label label]]
   [:div.col-md-7 input]])

(defn row4 [input1 input2 input3 input4]
  [:div.form-group
   [:div.col-md-3 input1]
   [:div.col-md-3 input2]
   [:div.col-md-2 input3]
   [:div.col-md-1 input4]])

(defn selected-index [event]
  (-> event .-target .-selectedIndex))

(defn str-empty [str]
  (or (nil? str) (= (count (str/trim str)) 0)))

(defn contains-or-empty? [search-field filter-value]
  #(or (str-empty filter-value) (not= -1 (.indexOf (search-field %) filter-value))))

(defn js-is-checked [event]
  (-> event .-target .-checked))

(defn with-index [coll]
  (map-indexed (fn [idx itm] [idx itm]) coll))

(defn indexes [coll]
  (map-indexed (fn [idx _] idx) coll))

(defn vec-remove
  "remove elem in coll"
  [coll pos]
  (vec (concat (subvec coll 0 pos) (subvec coll (inc pos)))))

(defn f-vec-remove [idx]
  #(vec-remove % idx))

(defn f-conj [value]
  #(conj % value))

(defmethod forms-core/init-field :input-validated
  [[type {:keys [id validate-func error-class] :as attrs}] {:keys [doc get save!]}]
  (render-element attrs doc
                  (let [value (or (get id) "")]
                    [type (merge
                            {:type      :text
                             :value     value
                             :class     (if (validate-func value) error-class "")
                             :on-change #(save! id (forms-core/value-of %))}
                            attrs)])))

(defn to-zero [v]
  (if (< v 0) 0 v))

(defn switch-page! [page]
  (session/put! :current-page page))

(defn gen-id []
  (.getTime (js/Date.)))

(defn validate-func [type not-null?]
  (fn [value]
    (let [reg (case type
                :string #".*"
                :long #"\d*"
                :int #"\d*"
                :short #"\d*"
                :double #"[0-9]{1,13}(\.[0-9]*)?"
                :float #"[0-9]{1,13}(\.[0-9]*)?"
                :boolean #"false|true|FALSE|TRUE"
                #".*")
          value-str (if (nil? value) "" value)]
      (or (and not-null? (empty? value)) (not (re-matches reg value-str))))))


(defn show-confirm-dialog [message command]
  (reagent-modals/modal!
    [:div.container-fluid
     [:div [:h3 message]]
     [:br]
     [:div.row
      [:div.col-md-6
       [make-simple-button "Ok" "glyphicon-ok" #(do (command)
                                                    (reagent-modals/close-modal!)) danger-button-block]]
      [:div.col-md-6
       [make-simple-button "Cancel" "glyphicon-remove" #(reagent-modals/close-modal!) blue-block-button]]]
     [:br]]
    {:size :sm}))

(defn or-property [value pr1 pr2]
  (let [v1 (pr1 value)
        v2 (pr2 value)]
    (if (not (clojure.string/blank? v1)) v1 v2)))

(def vec-sort-by
  (comp vec sort-by))

(defn in?
  "true if coll contains elm"
  [coll elm]
  (some #(= elm %) coll))

(defn swap-transform! [a path f]
  (swap! a #(s/transform path f %)))

(def not-in? (complement in?))