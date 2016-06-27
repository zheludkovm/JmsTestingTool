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
                                                        "setPort" [(Integer. port)]
                                                        "setChannel" [channel]
                                                        "setQueueManager" [queueManager]
                                                        "setCCSID" [(Integer. ccsid)]
                                                        "setTransportType" [1]
                                                        }})}
   })