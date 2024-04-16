#!/bin/bash

# Set Tomcat options
export CATALINA_OPTS="$CATALINA_OPTS -server -Xms1024m -Xmx3072m -Duser.timezone=America/New_York"
echo $CATALINA_OPTS