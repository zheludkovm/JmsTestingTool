(ns ru.jms.testingtool.mq-init
  (:import (clojure.lang Reflector))
  (:use ru.jms.testingtool.shared.mq_providers)
  )


(defn create-manual-qcf [{class :class constructor-parameters :constructor-parameters init-calls :init-calls}]
  (let [qcf (Reflector/invokeConstructor
              (resolve (symbol class))
              (to-array constructor-parameters))]
    (doall (for [[method-name params] init-calls]
             (Reflector/invokeInstanceMethod
               qcf
               method-name
               (to-array params))
             ))
    qcf))

(defn get-connection-info-field [connection-info field]
  (let [type (:type connection-info)]
    (-> providers
        type
        field)))

(defn create-qcf [connection-info]
  (let [init-fn (get-connection-info-field connection-info :init-fn)]
    (create-manual-qcf (init-fn connection-info))))

