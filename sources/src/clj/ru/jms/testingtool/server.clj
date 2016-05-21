(ns ru.jms.testingtool.server
  (:require [ru.jms.testingtool.handler :refer [app init-sente-handler]]
            [environ.core :refer [env]]
            [org.httpkit.server :refer [run-server]]

            [ru.jms.testingtool.dispatcher :as dispatcher]
            [ru.jms.testingtool.command :as command]
            )
  (:gen-class))

(defn -main [& args]
  (let [port (Integer/parseInt (or (env :port) "3000"))]
    (run-server app {:port port :join? false})
    (init-sente-handler dispatcher/process-in-command)
    (println "started!")))


