# ----------- Stage 1: Build i2b2-core-server WAR -----------
FROM amazonlinux:2023 AS builder

# Install build dependencies
RUN yum update -y && \
    yum install -y java-21-amazon-corretto-devel ant && \
    yum clean all && rm -rf /var/cache/yum

ENV JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto
ENV PATH=$JAVA_HOME/bin:$PATH

WORKDIR /build
COPY i2b2-core-server/ ./i2b2-core-server/
RUN cd i2b2-core-server/edu.harvard.i2b2.server-common && \
    ant clean dist war


FROM amazonlinux:2023

RUN yum update -y && \
    yum install -y java-21-amazon-corretto wget zip unzip tar gzip && \
    yum clean all && rm -rf /var/cache/yum

ENV JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto
ENV PATH=$JAVA_HOME/bin:$PATH

WORKDIR /opt/jboss

# --- Wildfly Setup ---
ARG WILDFLY_VERSION=37.0.1
RUN wget https://github.com/wildfly/wildfly/releases/download/${WILDFLY_VERSION}.Final/wildfly-${WILDFLY_VERSION}.Final.zip && \
    unzip wildfly-${WILDFLY_VERSION}.Final.zip && \
    mv wildfly-${WILDFLY_VERSION}.Final wildfly && \
    rm wildfly-${WILDFLY_VERSION}.Final.zip

# --- Application + config ---
COPY --from=builder /build/i2b2-core-server/edu.harvard.i2b2.server-common/dist/i2b2.war ./customization/

ARG HEAP_SIZE="-Xms1g -Xmx4g"
ENV JAVA_OPTS=$HEAP_SIZE
ENV JAVA_OPTS="$JAVA_OPTS -Dnet.snowflake.jdbc.enableBouncyCastle=TRUE"

# Common (non-secret) build assets: JDBC drivers (snowflake, postgres) + build-config.sh.
COPY configuration/common/* ./customization/
RUN chmod +x ./customization/build-config.sh

# THIS variant's datasource config (commands.cli + Snowflake private key), selected by the
# ENVIRONMENT build arg (dev | prod | shrine | shrine-prod | shrine-washu | shrine-washu-prod | local).
ARG ENVIRONMENT=dev
COPY configuration/${ENVIRONMENT}/ ./customization/

# Deploy the WAR and apply the datasource CLI to standalone.xml AT BUILD TIME.
RUN rm -rf /opt/jboss/wildfly/standalone/configuration/standalone_xml_history/* && \
    cp /opt/jboss/customization/i2b2.war /opt/jboss/wildfly/standalone/deployments/ && \
    touch /opt/jboss/wildfly/standalone/deployments/i2b2.war.dodeploy && \
    bash /opt/jboss/customization/build-config.sh

EXPOSE 8080 8009

VOLUME ["/tmp", \
        "/opt/jboss/wildfly/standalone/configuration", \
        "/opt/jboss/wildfly/standalone/data", \
        "/opt/jboss/wildfly/standalone/log", \
        "/opt/jboss/wildfly/standalone/tmp", \
        "/opt/jboss/wildfly/standalone/deployments"]

# Start WildFly with the baked (already-configured) standalone.xml.
CMD ["/opt/jboss/wildfly/bin/standalone.sh", "-b", "0.0.0.0"]
