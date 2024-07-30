##### BUILD STAGE 1 #############
FROM openjdk:8-jdk-alpine

ARG ENVIRONMENT
ENV WILDFLY_VERSION=17.0.1.Final

# Download required packages
RUN apk add \
    bash \
    apache-ant

WORKDIR /opt/jboss

RUN wget https://download.jboss.org/wildfly/$WILDFLY_VERSION/wildfly-$WILDFLY_VERSION.zip -O wildfly-$WILDFLY_VERSION.zip \
    && unzip wildfly-$WILDFLY_VERSION.zip && mv wildfly-$WILDFLY_VERSION wildfly \
    && rm -rf wildfly-$WILDFLY_VERSION.zip

# COPY FROM HOST CONTAINER
COPY /i2b2-core-server/  /tmp/i2b2-core-server/

# Build war from i2b2 server source code
RUN mkdir customization \
    && cd /tmp/i2b2-core-server/edu.harvard.i2b2.server-common \
    && ant clean dist war \
    && cp /tmp/i2b2-core-server/edu.harvard.i2b2.server-common/dist/i2b2.war /opt/jboss/customization/ \
    && rm -rf /tmp/i2b2-core-server

# RUN wildfly configuration
# https://goldmann.pl/blog/2014/07/23/customizing-the-configuration-of-the-wildfly-docker-image/
# FROM quay.io/wildfly/wildfly:26.1.2.Final

# Copy Required Script and snowflake JDBC driver
COPY /configuration/common/* /opt/jboss/customization/
COPY /configuration/${ENVIRONMENT}/commands.cli /opt/jboss/customization/

# run configuration script
RUN chmod +x /opt/jboss/customization/build-config.sh \
    && /opt/jboss/customization/build-config.sh

# Copy archieve configurations and deploy the WAR
RUN rm -rf /opt/jboss/wildfly/standalone/configuration/standalone_xml_history/* \
    && cp  /opt/jboss/customization/i2b2.war /opt/jboss/wildfly/standalone/deployments/ \
    && touch /opt/jboss/wildfly/standalone/deployments/i2b2.war.dodeploy

# PORTS
EXPOSE 8009 
# EXPOSE 9990
# Declare Volumes
VOLUME ["/tmp", "/opt/jboss/wildfly/standalone/data", "/opt/jboss/wildfly/standalone/log", "/opt/jboss/wildfly/standalone/tmp", "/opt/jboss/wildfly/standalone/deployments", "/opt/jboss/wildfly/standalone/configuration/"]
# Command for starting wildfly server
CMD ["/opt/jboss/wildfly/bin/standalone.sh", "--read-only-server-config=standalone.xml", "-b", "0.0.0.0"]
# CMD ["/opt/jboss/wildfly/bin/standalone.sh", "--read-only-server-config=standalone.xml", "-b", "0.0.0.0", "-bmanagement", "0.0.0.0"]