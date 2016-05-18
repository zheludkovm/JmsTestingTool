(ns ru.jms.testingtool.dispatcher
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.session :as session]
            [ru.jms.testingtool.utils :refer [js-println xor-assoc switch-page]]
            [ru.jms.testingtool.data :as data]

            [cljs.core.async :as async :refer (<! >! put! chan)]
            [taoensso.sente :as sente :refer (cb-success?)]
            [taoensso.encore :as enc :refer (tracef debugf infof warnf errorf)]
            )
  )


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

(defn init-sente-client [init-command dispatcher]
  (let [handler (fn [{[first second] :event}]
                  (if (= first :chsk/state)
                    (if (:open? second)
                      (do
                        (switch-page :home-page)
                        (init-command)
                        )
                      (switch-page :no-connection-page)
                      )
                    )

                  (if (and (vector? second) (= first :chsk/recv))
                    (let [[_ command] second]
                      (if (some? command) (dispatcher command)))
                    )
                  )]

    (sente/start-chsk-router! ch-chsk handler)))

(defmulti dispatch (fn [x] (x :direction)))
(defmulti dispatch-client (fn [x] (x :command)))

(defmethod dispatch :server [command]
  (chsk-send! [(:command command) command])
  (js-println "send to server1 " command))

(defmethod dispatch :client [command]
  (js-println "processing command " command)
  (dispatch-client command))

(defmethod dispatch :client-and-server [command]
  (js-println "processing command " command)
  (chsk-send! [(:command command) command])
  (dispatch-client command))




