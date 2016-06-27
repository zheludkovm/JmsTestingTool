(ns ru.jms.testingtool.shared.mq_providers)


(def providers
  {:generic   {:title       "Generic"
               :comment     "Generic provider, please manually edit config"
               :browse-type :any
               :fields      []
               :init-fn     identity}

   :activemq  {:title       "ActiveMQ"
               :comment     "ActiveMQ provider"
               :browse-type :browser
               :fields      [{:name "host" :field :host :type :string :not-null true}
                             {:name "port" :field :port :type :int :not-null true}]
               :init-fn     (fn [{host :host port :port}]
                              {:class                  "org.apache.activemq.ActiveMQConnectionFactory"
                               :constructor-parameters [(str "tcp://" host ":" port)]
                               :init-calls             {}})}

   :stomp     {:title       "Stomp"
               :comment     "Fuse jms stomp provider"
               :browse-type :consumer
               :fields      [{:name "host" :field :host :type :string :not-null true}
                             {:name "port" :field :port :type :int :not-null true}]
               :init-fn     (fn [{host :host port :port}]
                              {:class                  "org.fusesource.stomp.jms.StompJmsConnectionFactory"
                               :constructor-parameters []
                               :init-calls             {"setBrokerURI" [(str "tcp://" host ":" port)]}})}

   :amqp {:title       "AMQP"
               :comment     "AMQP QPID client"
               :browse-type :browser
               :fields      [{:name "remote URI (amqp://host:port)" :field :remoteURI :type :string :not-null true}]
               :init-fn     (fn [{remoteURI :remoteURI }]
                              {:class                  "org.apache.qpid.jms.JmsConnectionFactory"
                               :constructor-parameters []
                               :init-calls             {
                                                        "setRemoteURI" [remoteURI]
                                                        }})}
   :mq-series {:title       "IBM MQ"
               :comment     "IBM MQ Series"
               :browse-type :browser
               :fields      [{:name "host" :field :host :type :string :not-null true}
                             {:name "port" :field :port :type :int :not-null true}
                             {:name "channel" :field :channel :type :string :not-null true}
                             {:name "queue manager" :field :queueManager :type :string :not-null true}
                             {:name "CCSID" :field :ccsid :type :int :not-null true}
                             ]
               :init-fn     (fn [{host :host port :port channel :channel queueManager :queueManager ccsid :ccsid}]
                              {:class                  "com.ibm.mq.jms.MQConnectionFactory"
                               :constructor-parameters []
                               :init-calls             {
                                                        "setHostName" [host]
                                                        "setPort" [(read-string port)]
                                                        "setChannel" [channel]
                                                        "setQueueManager" [queueManager]
                                                        "setCCSID" [(read-string ccsid)]
                                                        "setTransportType" [1]
                                                        }})}
   })