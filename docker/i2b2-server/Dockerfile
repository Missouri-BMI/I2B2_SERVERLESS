# ----------- Stage 1: Build i2b2-core-server WAR -----------
FROM amazonlinux:2 AS builder

# Install build dependencies
RUN yum update -y && \
    yum install -y java-17-openjdk-devel ant wget unzip && \
    yum clean all && rm -rf /var/cache/yum

# Set correct JAVA_HOME for Amazon Linux 2
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk

# Add JAVA_HOME to PATH
ENV PATH=$JAVA_HOME/bin:$PATH


WORKDIR /build

# Copy source code
COPY i2b2-core-server/ ./i2b2-core-server/

# Build WAR file
RUN cd i2b2-core-server/edu.harvard.i2b2.server-common && \
    ant clean dist war

# ----------- Stage 2: Setup Wildfly and Deploy WAR -----------
FROM amazonlinux:2

# Install build dependencies
RUN yum update -y && \
    yum install -y java-17-openjdk-devel ant wget unzip && \
    yum clean all && rm -rf /var/cache/yum

# Set correct JAVA_HOME for Amazon Linux 2
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk

# Add JAVA_HOME to PATH
ENV PATH=$JAVA_HOME/bin:$PATH

ARG ENVIRONMENT
WORKDIR /opt/jboss

# --- Wildfly Setup ---
COPY wildfly-34.0.1.Final/ ./wildfly/

# --- Application Deployment ---
COPY --from=builder /build/i2b2-core-server/edu.harvard.i2b2.server-common/dist/i2b2.war ./customization/

# Enable BouncyCastle for Snowflake JDBC
RUN echo 'JAVA_OPTS="$JAVA_OPTS -Dnet.snowflake.jdbc.enableBouncyCastle=TRUE"' >> ./wildfly/bin/standalone.conf

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
VOLUME ["/tmp", "/opt/jboss/wildfly/standalone/data", "/opt/jboss/wildfly/standalone/log", "/opt/jboss/wildfly/standalone/tmp", "/opt/jboss/wildfly/standalone/deployments", "/opt/jboss/wildfly/standalone/configuration/"]

# Start Wildfly in read-only config mode
CMD ["/opt/jboss/wildfly/bin/standalone.sh", "--read-only-server-config=standalone.xml", "-b", "0.0.0.0"]

# RUN /opt/jboss/wildfly/bin/add-user.sh admin Password123 --silent
# CMD ["/opt/jboss/wildfly/bin/standalone.sh", "--read-only-server-config=standalone.xml", "-b", "0.0.0.0", "-bmanagement", "0.0.0.0"]