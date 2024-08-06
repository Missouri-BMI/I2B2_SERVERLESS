# Use Amazon Linux 2 as the base image
FROM amazonlinux:2



# Download required packages
# RUN apk add \
#     bash \
#     apache-ant
#     # Update the package list and install Java 8
RUN yum update -y && \
    yum install -y java-1.8.0-openjdk-devel ant wget unzip && \
    yum clean all

# Set JAVA_HOME environment variable
ENV JAVA_HOME /usr/lib/jvm/java-1.8.0

# Add JAVA_HOME to PATH
ENV PATH $JAVA_HOME/bin:$PATH

WORKDIR /opt/jboss

ARG ENVIRONMENT
ENV WILDFLY_VERSION=26.1.3.Final

RUN wget https://github.com/wildfly/wildfly/releases/download/$WILDFLY_VERSION/wildfly-$WILDFLY_VERSION.zip -O wildfly-$WILDFLY_VERSION.zip \
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