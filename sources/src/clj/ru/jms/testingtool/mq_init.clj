(ns ru.jms.testingtool.mq-init
  (:import (clojure.lang Reflector)))

(defmulti create-qcf (fn [x] (x :type)))

(defmethod create-qcf :generic [connection-info]
  (let [qcf (Reflector/invokeConstructor
              (resolve (symbol (:class connection-info)))
              (to-array (:constructor-parameters connection-info)))
        init-calls (:init-calls connection-info)]
    (doall (for [[method-name params] init-calls]
             (Reflector/invokeInstanceMethod
               qcf
               method-name
               (to-array params))
             ))
    qcf))