FROM azul/zulu-openjdk-centos:17
LABEL maintainer = "Harvard Catalyst Informatics"

ARG SCALA_VERSION
ARG KAFKA_VERSION

RUN yum install ca-certificates -y
RUN yum install -y wget
RUN yum install -y gettext
RUN yum install -y emacs
RUN wget https://archive.apache.org/dist/kafka/$KAFKA_VERSION/kafka_$SCALA_VERSION-$KAFKA_VERSION.tgz
RUN tar -xzf kafka_$SCALA_VERSION-$KAFKA_VERSION.tgz
WORKDIR kafka_$SCALA_VERSION-$KAFKA_VERSION
COPY docker/scripts/src/main/docker/images/kafka/server.properties.template config/server.properties.template
COPY docker/scripts/src/main/docker/images/kafka/kafka_server_jaas.conf /tmp/kafka_server_jaas.conf
COPY docker/scripts/src/main/docker/images/kafka/admin-config.properties /tmp/admin-config.properties
COPY docker/scripts/src/main/docker/images/kafka/startup.sh /tmp/startup.sh
COPY docker/scripts/src/main/docker/images/kafka/wait-for-it.sh /tmp/wait-for-it.sh

RUN chmod +x /tmp/startup.sh
RUN chmod +x /tmp/wait-for-it.sh

CMD /tmp/startup.sh
