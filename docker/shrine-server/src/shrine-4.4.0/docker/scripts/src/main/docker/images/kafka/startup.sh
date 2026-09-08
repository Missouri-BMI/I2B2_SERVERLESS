#!/usr/bin/env bash

echo "KAFKA_HOSTNAME: $KAFKA_HOSTNAME"
echo "BROKER_ID: $BROKER_ID"
echo "BROKER_PORT: $BROKER_PORT"
echo "CONTROLLER_PORT: $CONTROLLER_PORT"
echo "CONTROLLER1_PORT: $CONTROLLER1_PORT"
echo "CONTROLLER2_PORT: $CONTROLLER2_PORT"
echo "CONTROLLER3_PORT: $CONTROLLER3_PORT"

export KAFKA_OPTS="-Djava.security.auth.login.config=/tmp/kafka_server_jaas.conf"

cat config/server.properties.template | envsubst >> config/server.properties

echo
#Format kafka storage
mkdir /tmp/kraft-combined-logs

#TODO: revisit auto generating the random-uuid instead of using a static one
#randomUUID=$(./bin/kafka-storage.sh random-uuid)
 ./bin/kafka-storage.sh format -t CNs7cwnNTZ2BQXzPtetSRg -c config/server.properties

#Start Kafka
export KAFKA_OPTS="-Djava.security.auth.login.config=/tmp/kafka_server_jaas.conf"
nohup bin/kafka-server-start.sh -daemon config/server.properties \

/tmp/wait-for-it.sh ${KAFKA_HOSTNAME}:29092 -t 0
sleep 90s

tail -f /dev/null
