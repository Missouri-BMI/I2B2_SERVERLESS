#!/usr/bin/env bash

#wait a bit for the mysql database to be available
sleep 75s
echo "Running SHRINE Network lifecycle Tool. Will error-out if DB is not up yet. In that case, restart this container."

cd /tmp

unzip -o shrine-network-lifecycle-tool.zip
cp /tmp/override.conf shrine-network-lifecycle-tool-$1/conf

export JAVA_HOME=/usr/local/openjdk-17
export SHRINE_CONF=/usr/local/tomcat/lib/shrine.conf

echo $JAVA_HOME
echo $SHRINE_CONF

echo "Setting up SHRINE network"
./shrine-network-lifecycle-tool-$1/shrineLifecycle createNetwork network.conf
networkResult=$?

echo $networkResult

if [ "$networkResult" -ne 2 ]; then
    echo "Starting up tomcat"
    /usr/local/tomcat/bin/catalina.sh jpda run
fi
