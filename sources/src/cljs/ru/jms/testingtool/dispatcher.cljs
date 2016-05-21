(ns ru.jms.testingtool.dispatcher
  (:require [ru.jms.testingtool.utils :refer [js-println]]
            [taoensso.sente :as sente]))

; sente
;(sente/set-logging-level! :trace)
(defn exp-backoff "Returns binary exponential backoff value."
  [nattempt & [{:keys [factor] min' :min max' :max :or {factor 1000}}]]
  (let [max' 5000
        binary-exp (double (Math/pow 2 (dec ^long nattempt)))
        time (* (+ binary-exp ^double (rand binary-exp)) 0.5 (double factor))]
    (long (let [time (if min' (max (long min') (long time)) time)
                time (if max' (min (long max') (long time)) time)]
            time))))

(def packer :edn)
(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/chsk"                   ; Note the same path as before
                                  {:type          :auto     ; e/o #{:auto :ajax :ws}
                                   :packer        packer
                                   :backoff-ms-fn exp-backoff
                                   })]
  (def chsk chsk)
  (def ch-chsk ch-recv)                                     ; ChannelSocket's receive channel
  (def chsk-send! send-fn)                                  ; ChannelSocket's send API fn
  (def chsk-state state)                                    ; Watchable, read-only atom
  )

(defmulti send-command! (fn [x] (x :direction)))
(defmulti process-client-command (fn [x] (x :command)))

(defmethod send-command! :server [command]
  (chsk-send! [(:command command) command])
  (js-println "send to server " command))

(defmethod send-command! :client [command]
  (js-println "processing command " command)
  (process-client-command command))

(defn init-sente-client! [f-init f-not-connected]
  (let [handler (fn [{[first second] :event}]
                  (if (= first :chsk/state)
                    (if (:open? second)
                      (f-init)
                      (f-not-connected)))

                  (if (and (vector? second) (= first :chsk/recv))
                    (let [[_ command] second]
                      (if (some? command) (process-client-command command)))))]
    (sente/start-chsk-router! ch-chsk handler)))

