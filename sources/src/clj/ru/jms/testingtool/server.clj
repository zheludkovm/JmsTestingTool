(ns ru.jms.testingtool.server
  (:require [ru.jms.testingtool.handler :refer [app]]
            [environ.core :refer [env]]
            [org.httpkit.server :refer [run-server]]

            )
  (:gen-class))

(defn -main [& args]
  (let [port (Integer/parseInt (or (env :port) "3000"))]
    (run-server app {:port port :join? false})
    (println "started!")))
