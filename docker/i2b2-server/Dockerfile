# ----------- Stage 1: Build i2b2-core-server WAR -----------
FROM amazonlinux:2023 AS builder

# Install build dependencies
RUN yum update -y && \
    yum install -y java-21-amazon-corretto-devel ant && \
    yum clean all && rm -rf /var/cache/yum

# Set correct JAVA_HOME for Amazon Linux 2023
ENV JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto


# Add JAVA_HOME to PATH
ENV PATH=$JAVA_HOME/bin:$PATH


WORKDIR /build

# Copy source code
COPY i2b2-core-server/ ./i2b2-core-server/

# Build WAR file
RUN cd i2b2-core-server/edu.harvard.i2b2.server-common && \
    ant clean dist war

# ----------- Stage 2: Setup Wildfly and Deploy WAR -----------
FROM amazonlinux:2023

# Install Java 21 runtime
RUN yum update -y && \
    yum install -y java-21-amazon-corretto wget zip && \
    yum clean all && rm -rf /var/cache/yum

# Set correct JAVA_HOME for Amazon Linux 2023
ENV JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto

# Add JAVA_HOME to PATH
ENV PATH=$JAVA_HOME/bin:$PATH

ARG ENVIRONMENT
WORKDIR /opt/jboss

# --- Wildfly Setup ---
ARG WILDFLY_VERSION=37.0.1
RUN wget https://github.com/wildfly/wildfly/releases/download/${WILDFLY_VERSION}.Final/wildfly-${WILDFLY_VERSION}.Final.zip && \
    unzip wildfly-${WILDFLY_VERSION}.Final.zip && \
    mv wildfly-${WILDFLY_VERSION}.Final wildfly && \
    rm wildfly-${WILDFLY_VERSION}.Final.zip

# --- Application Deployment ---
COPY --from=builder /build/i2b2-core-server/edu.harvard.i2b2.server-common/dist/i2b2.war ./customization/

# Set JVM HEAP Allocation and enable BouncyCastle for Snowflake JDBC
# Define a build argument for the heap size with a default value (e.g., for dev)
ARG HEAP_SIZE="-Xms1g -Xmx4g"

# Set the JAVA_OPTS environment variable using the build argument
ENV JAVA_OPTS=$HEAP_SIZE

# Append additional, non-dynamic JVM options
ENV JAVA_OPTS="$JAVA_OPTS -Dnet.snowflake.jdbc.enableBouncyCastle=TRUE"

# Configuration files
COPY configuration/common/* ./customization/
COPY configuration/${ENVIRONMENT}/commands.cli ./customization/
COPY configuration/${ENVIRONMENT}/rsa_key.p8 ./customization/

# Build and configure
RUN chmod +x ./customization/build-config.sh && \
    ./customization/build-config.sh

# Deploy WAR into Wildfly
RUN rm -rf /opt/jboss/wildfly/standalone/configuration/standalone_xml_history/* \
    && cp  /opt/jboss/customization/i2b2.war /opt/jboss/wildfly/standalone/deployments/ \
    && touch /opt/jboss/wildfly/standalone/deployments/i2b2.war.dodeploy

# Expose ports
EXPOSE 8080 8009
# EXPOSE 8080 8009 9990

# Optional: Only mount persistent folders
VOLUME ["/tmp", "/opt/jboss/wildfly/standalone/data", "/opt/jboss/wildfly/standalone/log", "/opt/jboss/wildfly/standalone/tmp", "/opt/jboss/wildfly/standalone/deployments"]

# Start Wildfly in read-only config mode
CMD ["/opt/jboss/wildfly/bin/standalone.sh", "--read-only-server-config=standalone.xml", "-b", "0.0.0.0"]

# RUN /opt/jboss/wildfly/bin/add-user.sh admin Password123 --silent
# CMD ["/opt/jboss/wildfly/bin/standalone.sh", "--read-only-server-config=standalone.xml", "-b", "0.0.0.0", "-bmanagement", "0.0.0.0"]