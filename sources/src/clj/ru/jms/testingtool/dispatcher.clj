(ns ru.jms.testingtool.dispatcher
  (:import (java.io IOException)))

(def ^:dynamic sender-id :sente/all-users-without-uid)

(def ^:dynamic send-to-client! #(throw (Exception. "send not initialized!")))

(defmulti process-server-command (fn [x] (x :command)))

(defmulti send-command! (fn [x] (x :direction)))

(defmethod send-command! :client [command]
  (send-to-client! sender-id [(:command command) command]))

(defn process-error [e message]
  (send-command! {:direction :client :command (keyword "ru.jms.testingtool.command" "add-log-entry") :message message :level "alert-danger"})
  (throw e))

(defmethod send-command! :server [command]
  ;(println "processing command " command)
  (try
    (process-server-command command)
    (catch IOException e
      (process-error e "Can't connect to queue!"))
    (catch Exception e
      (process-error e (str "Error during processing request! " (.getMessage e))))))

(defn process-in-command [p-sender-id p-send-to-client-fn! command]
  (if (some? command)
    (with-bindings {#'sender-id       p-sender-id
                    #'send-to-client! p-send-to-client-fn!}
      (send-command! command))))
