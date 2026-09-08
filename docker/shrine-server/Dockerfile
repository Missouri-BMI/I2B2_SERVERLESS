FROM amazonlinux:2023

ARG TOMCAT_VERSION=9.0.121
ENV CATALINA_HOME=/usr/local/tomcat
ENV PATH=$CATALINA_HOME/bin:$PATH

RUN dnf -y update && \
    dnf -y install tar gzip unzip wget java-17-amazon-corretto-headless mariadb105 gawk && \
    dnf clean all && rm -rf /var/cache/dnf

# Install Tomcat and strip the default webapps to reduce the attack surface.
RUN mkdir -p "$CATALINA_HOME" && \
    wget -q "https://archive.apache.org/dist/tomcat/tomcat-9/v${TOMCAT_VERSION}/bin/apache-tomcat-${TOMCAT_VERSION}.tar.gz" -O /tmp/tomcat.tgz && \
    tar xzf /tmp/tomcat.tgz -C "$CATALINA_HOME" --strip-components=1 && \
    rm -f /tmp/tomcat.tgz && \
    rm -rf "$CATALINA_HOME"/webapps/examples "$CATALINA_HOME"/webapps/docs \
           "$CATALINA_HOME"/webapps/manager "$CATALINA_HOME"/webapps/host-manager \
           "$CATALINA_HOME"/webapps/ROOT

# WAR (application artifact) + common (non-secret) executables.
COPY src/shrine-4.4.1/shrine-api.war /usr/local/tomcat/webapps/

COPY src/executables/setenv.sh /usr/local/tomcat/bin/setenv.sh
COPY src/executables/docker-entrypoint.sh /usr/local/tomcat/bin/docker-entrypoint.sh
COPY src/executables/mariadb-java-client-3.3.2.jar /usr/local/tomcat/lib/

# SHRINE 4.4.1 schema DDLs (ADAPTER/QEP) loaded by the entrypoint before Tomcat starts.
COPY shrine-setup/ /usr/local/tomcat/lib/shrine-setup/

ARG ENVIRONMENT=dev
ARG PROJECT=mu
COPY src/configs/${ENVIRONMENT}/${PROJECT}/context.xml   /usr/local/tomcat/conf/context.xml
COPY src/configs/${ENVIRONMENT}/${PROJECT}/server.xml    /usr/local/tomcat/conf/server.xml
COPY src/configs/${ENVIRONMENT}/${PROJECT}/creds.conf    /usr/local/tomcat/lib/creds.conf
COPY src/configs/${ENVIRONMENT}/${PROJECT}/shrine.conf   /usr/local/tomcat/lib/shrine.conf
COPY src/keystore/${ENVIRONMENT}/${PROJECT}/output/      /usr/local/tomcat/lib/


COPY src/data/act-4.2/lucene_index/       /usr/local/tomcat/lib/lucene_index/
COPY src/data/act-4.2/suggest_index/      /usr/local/tomcat/lib/suggest_index/
COPY src/data/act-4.2/AdapterMappings.csv /usr/local/tomcat/lib/AdapterMappings.csv
COPY src/data/act-4.2/ENACT_LogoFile.png  /usr/local/tomcat/lib/ENACT_LogoFile.png



RUN chmod +x /usr/local/tomcat/bin/setenv.sh /usr/local/tomcat/bin/docker-entrypoint.sh


EXPOSE 6060


# forward request and error logs to docker log collector
RUN ln -sf /dev/stdout /usr/local/tomcat/logs/shrine-api.log

# Declare Volumes
VOLUME ["/usr/local/tomcat"]

# Ensure the ADAPTER/QEP databases + schemas exist, then run Tomcat in the foreground.
ENTRYPOINT ["/usr/local/tomcat/bin/docker-entrypoint.sh"]
CMD ["/usr/local/tomcat/bin/catalina.sh", "run", "-d"]
