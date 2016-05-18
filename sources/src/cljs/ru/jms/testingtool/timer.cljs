(ns ru.jms.testingtool.timer
  (:require [ru.jms.testingtool.data :as data]
            [ru.jms.testingtool.utils :refer [js-println xor-assoc]]))

(def TIME-ALIVE 5000)

(defn is-old [t1 log-entry]
  (if (nil? log-entry)
    false
    (< (.getTime (:time log-entry)) t1)))

(defn clear-old-log-messages2 []
  (let [old-log-entries (:log-entries @data/web-data)
        t (- (.getTime (js/Date.)) TIME-ALIVE)]
    (if (is-old t (last old-log-entries))
      (swap! data/web-data assoc :log-entries (butlast old-log-entries)))))

(defonce time-updater
         (js/setInterval clear-old-log-messages2 1000))

