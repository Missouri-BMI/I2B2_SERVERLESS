FROM tomcat:9.0.62-jdk17-openjdk
LABEL maintainer = "Harvard Catalyst Informatics"
#TODO - use dockerize templating for context.xml from compose file? if we want a whole shrine network
#ITEAM NOTE - this is where context files should be on all our stacks

ARG SHRINE_VERSION
COPY docker/scripts/src/main/docker/images/shrine-node/server.xml /usr/local/tomcat/conf/server.xml
COPY docker/scripts/src/main/docker/images/shrine-node/context.xml /usr/local/tomcat/conf/Catalina/localhost/shrine-api.xml
COPY docker/scripts/src/main/docker/images/shrine-node/password.conf /usr/local/tomcat/lib/password.conf
COPY docker/scripts/src/main/docker/images/shrine-node/shrine.conf /usr/local/tomcat/lib/shrine.conf
COPY docker/scripts/src/main/docker/images/shrine-node/setenv.sh /usr/local/tomcat/bin/setenv.sh
COPY apps/shrine-api-war/target/shrine-api-war-${SHRINE_VERSION}.war /usr/local/tomcat/webapps/shrine-api.war
COPY hub/network-lifecycle-tool/target/shrine-network-lifecycle-tool-${SHRINE_VERSION}-dist.zip /tmp/shrine-network-lifecycle-tool.zip
COPY qep/researcher-metrics-tool/target/shrine-researcher-metrics-${SHRINE_VERSION}-dist.zip /tmp/researcher-metrics-tool.zip

RUN wget -P /usr/local/tomcat/lib https://repo1.maven.org/maven2/mysql/mysql-connector-java/8.0.28/mysql-connector-java-8.0.28.jar
RUN apt-get update -y \
    && apt-get install -y wait-for-it



