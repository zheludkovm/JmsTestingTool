(ns ru.jms.testingtool.shared.common-utils
  (:require [com.rpl.specter :as s]))

(defn find-first [coll attr attr-value]
  (first
    (filter
      #(= attr-value (get % attr))
      coll
      )))

(defn GET-BY-ID [id]
  [s/FIRST #(= (:id %) id)]
  )

(defn ALL-GET-BY-ID [id]
  [s/ALL #(= (:id %) id)]
  )

(defn ALL-GET-BY-ID-LIST [id-list]
  [s/ALL #(contains? id-list (:id %))]
  )