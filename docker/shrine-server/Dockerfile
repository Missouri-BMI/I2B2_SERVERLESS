# Use the CentOS base image
FROM tomcat:9-jdk17-corretto

ARG ENVIRONMENT=dev
ARG PROJECT=mu

RUN yum -y update && \
    yum install -y \
    postfix \
    ntp \
    wget \
    tar \
    unzip \
    md5sum && \
    yum clean all

## copy executables
COPY src/executables/shrine-api-war-3.3.1.war /usr/local/tomcat/webapps/shrine-api.war
COPY src/executables/commands.sh /etc/init.d/
COPY src/executables/mariadb-java-client-3.3.2.jar /usr/local/tomcat/lib/
COPY src/executables/setenv.sh /usr/local/tomcat/bin/setenv.sh 

## copy configs
COPY src/configs/${PROJECT}/context.xml /usr/local/tomcat/conf/
COPY src/configs/${PROJECT}/password.conf /usr/local/tomcat/lib/
COPY src/configs/${PROJECT}/server.xml /usr/local/tomcat/conf/
COPY src/configs/${PROJECT}/shrine.conf /usr/local/tomcat/lib/
COPY src/configs/logging.properties /usr/local/tomcat/conf/
# COPY src/configs/logging.shrine-api.properties /usr/local/tomcat/webapps/shrine-api/WEB-INF/classes/logging.properties

## copy keystore
COPY src/keystore/${PROJECT}/output/ /usr/local/tomcat/

## copy data
COPY src/data/ENACT_LogoFile_2023-03_White.png /usr/local/tomcat/lib/
COPY src/data/AdapterMappingV41012224.csv /usr/local/tomcat/lib/

# RUN cd /usr/local/tomcat/lib/ && \
#     wget https://shrine-ontologies.s3.amazonaws.com/act/releases/lucene_index-4.1.0-SHRINE-4.2.0.zip -O lucene_index.zip && \
#     wget https://shrine-ontologies.s3.amazonaws.com/act/releases/suggest_index-4.1.0-SHRINE-4.2.0.zip -O suggest_index.zip && \
#     unzip lucene_index.zip && \
#     unzip suggest_index.zip && \
#     rm lucene_index.zip suggest_index.zip

COPY src/data/lucene_index/ /usr/local/tomcat/lib/lucene_index/
COPY src/data/suggest_index/ /usr/local/tomcat/lib/suggest_index/


RUN chmod +x /etc/init.d/commands.sh \ 
            /usr/local/tomcat/bin/setenv.sh \
            /usr/local/tomcat/conf/context.xml \
            /usr/local/tomcat/lib/shrine.conf \
            /usr/local/tomcat/lib/password.conf \
            /usr/local/tomcat/conf/logging.properties \
            # /usr/local/tomcat/webapps/shrine-api/WEB-INF/classes/logging.properties \
            /usr/local/tomcat/lib/ENACT_LogoFile_2023-03_White.png \
            /usr/local/tomcat/lib/AdapterMappingV41012224.csv \
            # /usr/local/tomcat/shrine-dev-nextgenbmi.jks \
            /usr/local/tomcat/conf/server.xml

EXPOSE 6060

# forward request and error logs to docker log collector
RUN ln -sf /dev/stdout /usr/local/tomcat/logs/shrine-api.log
	# && ln -sf /dev/stderr /var/log/nginx/error.log

# Declare Volumes
VOLUME ["/usr/local/tomcat"]

# Run the script when the container starts
CMD ["/etc/init.d/commands.sh"]
