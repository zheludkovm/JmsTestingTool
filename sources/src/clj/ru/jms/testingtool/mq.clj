(ns ru.jms.testingtool.mq
  (:import (javax.jms QueueConnectionFactory Connection Session Queue MessageConsumer Message TextMessage QueueBrowser MessageProducer BytesMessage)
           (java.util Enumeration)
           (java.io IOException))
  (:use ru.jms.testingtool.mq-init))

(defn ^Session get-session
  ([connection-info ack-mode]
   (try
     (let [^QueueConnectionFactory qcf (create-qcf connection-info)
           ^Connection q (if (clojure.string/blank? (:user connection-info))
                           (.createConnection qcf)
                           (.createConnection qcf (:user connection-info) (:password connection-info)))
           _ (.start q)]
       (.createSession q false ack-mode))
     (catch Exception e
       (throw (IOException. e)))))
  ([connection-info]
   (get-session connection-info Session/AUTO_ACKNOWLEDGE)))


(defn ^Queue get-queue [^Session s queue-info]
  (.createQueue s (:name queue-info)))

(defn safe-destination-to-str [destination]
  (if (nil? destination)
    nil
    (.getQueueName destination)))

(defn safe-to-long [^Long val]
  (if (nil? val) nil (Long. (str val))))

(defn safe-to-int [val]
  (if (nil? val) nil (Integer. (str val))))

(defn safe-to-short [val]
  (if (nil? val) nil (Short. (str val))))

(defn safe-to-double [val]
  (if (nil? val) nil (Double. (str val))))

(defn safe-to-float [val]
  (if (nil? val) nil (Float. (str val))))

(defn safe-to-boolean [val]
  (if (nil? val) nil (Boolean. (str val))))

(defn safe-to-destination [^Session s ^String queue-name]
  (if (nil? queue-name) nil (.createQueue s queue-name)))

(defn safe-to-str [val]
  (if (nil? val) nil (str val)))

(defn get-message-body [^Message msg]
  (cond
    (instance? TextMessage msg) (.getText msg)
    (instance? BytesMessage msg) (let [^BytesMessage bytesMsg msg
                                       len (.getBodyLength bytesMsg)
                                       buffer (byte-array len)
                                       _ (.readBytes bytesMsg buffer)]
                                   (String. buffer "UTF-8")
                                   )
    :else (throw (IOException. (str "Not supported message class!" (.getClass msg))))))

(defn convert-message [^Message msg]
  (if (some? msg)
    (let [^String text (get-message-body msg)
          ^String text2 (if (nil? text) "" text)
          text-len (.length text2)
          res {:id               (.getJMSMessageID msg)
               :jmsMessageId     (.getJMSMessageID msg)
               :type             :string
               :short-title      (subs text2 0 (min 100 text-len))
               :long-title       (subs text2 0 (min 8000 text-len))
               :size             text-len
               :jmsCorrelationId (.getJMSCorrelationID msg)
               :jmsExpiration    (.getJMSExpiration msg)
               :jmsPriority      (.getJMSPriority msg)
               :jmsTimestamp     (safe-to-str (.getJMSTimestamp msg))
               :jmsReplyTo       (safe-destination-to-str (.getJMSReplyTo msg))
               :headers          (into [] (for [property-name (enumeration-seq (.getPropertyNames msg))]
                                            {:name  property-name
                                             :value (.getStringProperty msg property-name)
                                             :type  :string}))}]
      ;(println "message! " res)
      res)))

(defn safe-call [fn convert-fn param]
  (if (some? param) (fn (convert-fn param))))

(defn convert-message-to-mq [^Session s message]
  (let [^TextMessage mq-message (.createTextMessage s)]
    (.setJMSCorrelationID mq-message (:jmsCorrelationId message))
    (safe-call #(.setJMSExpiration mq-message %) safe-to-long (:jmsExpiration message))
    (safe-call #(.setJMSPriority mq-message %) safe-to-int (:jmsPriority message))
    (safe-call #(.setJMSReplyTo mq-message %) #(safe-to-destination s %) (:jmsReplyTo message))
    (doall (for [header (:headers message)]
             (let [name (:name header)
                   value (:value header)
                   type (:type header)
                   ]
               (if (and (some? name) (some? value))
                 (case type
                   :string (.setStringProperty mq-message name value)
                   :long (.setLongProperty mq-message name (safe-to-long value))
                   :int (.setIntProperty mq-message name (safe-to-int value))
                   :short (.setShortProperty mq-message name (safe-to-short value))
                   :double (.setDoubleProperty mq-message name (safe-to-double value))
                   :float (.setShortProperty mq-message name (safe-to-float value))
                   :boolean (.setShortProperty mq-message name (safe-to-boolean value)))))))
    (.setText mq-message (:long-title message))
    mq-message))

(defn get-all [^MessageConsumer consumer]
  (->> (repeatedly #(.receive consumer 1000))
       (take-while some?)))

(defn consume-queue [connection-info queue-info]
  (let [^Session s (get-session connection-info Session/CLIENT_ACKNOWLEDGE)
        ^Queue q (get-queue s queue-info)
        ^MessageConsumer consumer (.createConsumer s q)
        messages (get-all consumer)
        converted-messages (doall (map convert-message messages))]
    (.close consumer)
    (.close s)
    (doall converted-messages)))

(defn browse-queue [connection-info queue-info]
  (let [^Session s (get-session connection-info)
        ^Queue q (get-queue s queue-info)
        ^QueueBrowser browser (.createBrowser s q)
        ^Enumeration msgs-e (.getEnumeration browser)
        messages (enumeration-seq msgs-e)
        converted-messages (doall (for [msg messages]
                                    (convert-message msg)
                                    ))]
    (.close browser)
    (.close s)
    converted-messages))

(defn browse-queue-messages [connection-info queue-info]
  (let [provider-browse-type (get-connection-info-field connection-info :browse-type)
        connection-browse-type (:browse-type connection-info)]
    (if (or (= provider-browse-type :consumer)
            (and (= provider-browse-type :any)
                 (= connection-browse-type :consumer)))
      (consume-queue connection-info queue-info)
      (browse-queue connection-info queue-info))))

(defn send-messages! [connection-info queue-info messages]
  (let [^Session s (get-session connection-info)
        ^Queue q (get-queue s queue-info)
        ^MessageProducer producer (.createProducer s q)]
    (doall (for [message messages
                 :let [mq-message (convert-message-to-mq s message)]]
             (.send producer mq-message)))
    (.close producer)
    (.close s)))

(defn purge-queue! [connection-info queue-info]
  (let [^Session s (get-session connection-info)
        ^Queue q (get-queue s queue-info)
        ^MessageConsumer consumer (.createConsumer s q)]
    (doall (get-all consumer))
    (.close consumer)
    (.close s)))