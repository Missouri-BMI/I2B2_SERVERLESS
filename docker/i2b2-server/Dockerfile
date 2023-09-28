FROM alpine:3.14 AS war_builder

# Download required packages
RUN apk add --no-cache \
    openjdk8 \
    apache-ant \
    wget \
    unzip \
    bash

# Download i2b2 server source code
ARG BRANCH=feature/mu

RUN wget https://github.com/Missouri-BMI/i2b2-core-server/archive/$BRANCH.tar.gz -O /tmp/i2b2.tar.gz

# Download the source code
RUN mkdir /tmp/i2b2-core-server ;\
    tar --strip-components=1 -zxvf /tmp/i2b2.tar.gz -C /tmp/i2b2-core-server/ ;\
    rm -rf /tmp/i2b2.tar.gz

# Build war from i2b2 server source code
RUN cd /tmp/i2b2-core-server/edu.harvard.i2b2.server-common && ant clean dist war

FROM jboss/wildfly:17.0.1.Final

# Copy Required Script and JDBC drivers
RUN mkdir ./wildfly/customization/ 
COPY ./customization/ ./wildfly/customization/

# copy war file
COPY --from=war_builder /tmp/i2b2-core-server/edu.harvard.i2b2.server-common/dist/i2b2.war ./wildfly/customization/

# Define Environment Variables
EXPOSE 8009

# Clear configuration History
RUN rm -rf /opt/jboss/wildfly/standalone/configuration/standalone_xml_history

# Run custom script
CMD ["/bin/bash", "-C", "/opt/jboss/wildfly/customization/execute.sh"]