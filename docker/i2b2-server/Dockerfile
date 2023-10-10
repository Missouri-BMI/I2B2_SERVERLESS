##### BUILD STAGE 1 #############
FROM alpine:3.18.4 AS war_builder

# Download required packages
RUN apk add \
    openjdk8 \
    apache-ant \
    wget \
    unzip

# # COPY from host container
# COPY /i2b2-core-server/ /tmp/i2b2-core-server/

# Download i2b2 server source code
ARG BRANCH=feature/mu

RUN wget https://github.com/Missouri-BMI/i2b2-core-server/archive/$BRANCH.tar.gz -O /tmp/i2b2.tar.gz

# Download the source code
RUN mkdir /tmp/i2b2-core-server ;\
    tar --strip-components=1 -zxvf /tmp/i2b2.tar.gz -C /tmp/i2b2-core-server/ ;\
    rm -rf /tmp/i2b2.tar.gz

# Build war from i2b2 server source code
RUN cd /tmp/i2b2-core-server/edu.harvard.i2b2.server-common && ant clean dist war

##### BUILD STAGE 2 #############
# RUN wildfly configuration
# https://goldmann.pl/blog/2014/07/23/customizing-the-configuration-of-the-wildfly-docker-image/

# FROM quay.io/wildfly/wildfly:26.1.2.Final
FROM jboss/wildfly:17.0.1.Final

# ARGUMENTS
ARG ENVIRONMENT

# Copy Required Script 
COPY ./configuration/common/ /opt/jboss/wildfly/customization/
COPY ./configuration/${ENVIRONMENT}/commands.cli /opt/jboss/wildfly/customization/

# copy war file and JDBC drivers
COPY --from=war_builder /tmp/i2b2-core-server/edu.harvard.i2b2.server-common/dist/i2b2.war /opt/jboss/wildfly/standalone/deployments/

RUN  rm -rf /tmp/i2b2-core-server/

USER root

RUN chown jboss:jboss /opt/jboss/wildfly/standalone/deployments/*
RUN chown jboss:jboss /opt/jboss/wildfly/customization/*
    
USER jboss

RUN chmod 777 /opt/jboss/wildfly/standalone/deployments/*
RUN chmod 777 /opt/jboss/wildfly/customization/*

# run configuration script
RUN /opt/jboss/wildfly/customization/execute.sh

# PORTS
EXPOSE 8009
EXPOSE 8080
EXPOSE 9990

#Optional: Add admin user
RUN /opt/jboss/wildfly/bin/add-user.sh admin Admin#70365 --silent

# Clear configuration History
RUN rm -rf /opt/jboss/wildfly/standalone/configuration/standalone_xml_history

# Administration console will be available on the port 9990 of the container.
CMD ["/opt/jboss/wildfly/bin/standalone.sh", "-b", "0.0.0.0", "-bmanagement", "0.0.0.0"]
# CMD ["/opt/jboss/wildfly/bin/standalone.sh", "-b", "0.0.0.0"]