#!/bin/bash


# echo 'ping to shrine hub ip address.........'
# curl -kw "\n%{response_code}\n" -X GET https://shrine-act-test.hms.harvard.edu:6443/shrine-api/hub/ping


/usr/local/tomcat/bin/catalina.sh run -d
