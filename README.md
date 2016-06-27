#JMS Testing tool

This tool intended to work with JMS :

- send messages to queue

- store persistent message collections

- observe queue messages

##Installation

Prerequisite : installed java 8 sdk

git clone https://github.com/zheludkovm/JmsTestingTool.git

cd JmsTestingTool/build

if you use linux :

./run-server.sh

if you use windows :

run-server.bat

Open browser url  http://localhost:3000

###Supported JMS Providers

- Active MQ http://activemq.apache.org/

- Stomp protocol https://github.com/fusesource/stompjms

- IBM MQ http://www-03.ibm.com/software/products/ru/ibm-mq

  (you should download ibm mq client jars from ibm site and extract to directory build/lib-ext)
  
- Generic JMS provider
 
  download provider jars to build/lib-ext

  manually add connection to file build/config.edn
  
  for example generic config for IBM MQ (don't use it in real life, IBM MQ is supported provider)
  
  >{:id :generic-test,
  
  > :type :generic,
  
  > :title "generic",
   
  > :class "com.ibm.mq.jms.MQConnectionFactory",
   
  > :constructor-parameters [],
   
  > :init-calls {"setHostName" ["127.0.0.1"]
   
  >              "setPort" [1414]
                
  >              "setChannel" ["CHANNEL.NAME"]
                
  >              "setQueueManager" ["QM_1"]
                
  >              "setTransportType" [1]
  
  >            },
              
  > :browse-type :browser,
   
  > :queues[{:name "queue1", :title "queu1", :id :test-queue1}]}

###Screenshots :

![Main window](/readme-ext/main-window.png)

![Edit message](/readme-ext/edit-message.png)

![Config](/readme-ext/config.png)
