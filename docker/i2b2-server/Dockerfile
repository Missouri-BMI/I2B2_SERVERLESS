##### BUILD STAGE 1 #############
FROM openjdk:8-jdk-alpine AS war_builder

# Download required packages
RUN apk add \
    apache-ant

# Download i2b2 server source code
# ARG BRANCH=feature/mu

# RUN wget https://github.com/Missouri-BMI/i2b2-core-server/archive/$BRANCH.tar.gz -O /tmp/i2b2.tar.gz

# # Download the source code
# RUN mkdir /tmp/i2b2-core-server \
#     && tar --strip-components=1 -zxvf /tmp/i2b2.tar.gz -C /tmp/i2b2-core-server/ \
#     &&  rm -rf /tmp/i2b2.tar.gz

# COPY FROM HOST CONTAINER
COPY /i2b2-core-server/  /tmp/i2b2-core-server/

# Build war from i2b2 server source code
RUN cd /tmp/i2b2-core-server/edu.harvard.i2b2.server-common && ant clean dist war

##### BUILD STAGE 2 #############
# RUN wildfly configuration
# https://goldmann.pl/blog/2014/07/23/customizing-the-configuration-of-the-wildfly-docker-image/

FROM quay.io/wildfly/wildfly:26.1.2.Final

COPY --from=war_builder /tmp/i2b2-core-server/edu.harvard.i2b2.server-common/dist/i2b2.war /opt/jboss/customization/


# ARGUMENTS
ARG ENVIRONMENT

# Copy Required Script and snowflake JDBC driver
COPY --chown=jboss:jboss ./configuration/common/ /opt/jboss/customization/
COPY --chown=jboss:jboss ./configuration/${ENVIRONMENT}/commands.cli /opt/jboss/customization/

# run configuration script
RUN chmod +x /opt/jboss/customization/build-config.sh \
    && /opt/jboss/customization/build-config.sh

# Copy archieve configurations and deploy the WAR
RUN rm -rf /opt/jboss/wildfly/standalone/configuration/standalone_xml_history/* \
    && cp  /opt/jboss/customization/i2b2.war /opt/jboss/wildfly/standalone/deployments/ \
    && touch /opt/jboss/wildfly/standalone/deployments/i2b2.war.dodeploy

# PORTS
EXPOSE 8009
# EXPOSE 8080
# EXPOSE 9990

# Declare Volumes
VOLUME ["/tmp", "/opt/jboss/wildfly/standalone/data", "/opt/jboss/wildfly/standalone/log", "/opt/jboss/wildfly/standalone/tmp", "/opt/jboss/wildfly/standalone/deployments", "/opt/jboss/wildfly/standalone/configuration/standalone_xml_history"]

# Command for starting wildfly server
CMD ["/opt/jboss/wildfly/bin/standalone.sh", "--read-only-server-config=standalone.xml", "-b", "0.0.0.0"]

# /opt/jboss/wildfly/bin/add-user.sh admin Admin#70365 --silent
# /opt/jboss/wildfly/bin/standalone.sh --read-only-server-config=standalone.xml -b 0.0.0.0 -bmanagement 0.0.0.0