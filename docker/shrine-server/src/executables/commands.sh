#!/bin/bash


# echo 'ping to shrine hub ip address.........'
# curl -kw "\n%{response_code}\n" -X GET https://shrine-act-test.hms.harvard.edu:6443/shrine-api/hub/ping


# Start Shibd
/etc/shibboleth/shibd-redhat start

# Start httpd
# exec httpd -DFOREGROUND

# Start tomcat shrine
/usr/local/tomcat/bin/catalina.sh run -d