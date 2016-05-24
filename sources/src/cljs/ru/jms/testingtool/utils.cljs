(ns ru.jms.testingtool.utils
  (:require-macros [reagent-forms.macros :refer [render-element]])
  (:require [reagent-forms.core :as forms-core]
            [clojure.string :as str]
            [reagent.session :as session]))

(def gray-block-button "btn btn-default btn-block")
(def blue-block-button "btn btn-primary btn-block")
(def blue-button "btn btn-primary")
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
             ;:class    "btn btn"
             :title    text
             :value    text
             :disabled (disabled-function)
             :on-click on-click
             }
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