touch log.txt

$JAVA_HOME/bin/java -cp .:lib/jms-testing-tool.jar:lib-ext/* ru.jms.testingtool.server > log.txt &

PID_SERVER=$!

echo $PID_SERVER

tail -f log.txt | while read LOGLINE
do
   [[ "${LOGLINE}" == *"started"* ]] && pkill -P $$ tail
done

chromium-browser --temp-profile --new-window --app=http://localhost:3000/  --start-maximized

kill -9 ${PID_SERVER}
