FROM mysql:8.0.35-debian
LABEL maintainer = "Harvard Catalyst Informatics"
ENV MYSQL_ROOT_PASSWORD password
RUN mkdir data

COPY shrine-setup/target/shrine-setup-*-dist.zip data/
RUN apt-get update -y && apt-get install -y unzip
RUN unzip data/shrine-setup-*-SNAPSHOT-dist.zip -d data
COPY docker/scripts/src/main/docker/images/mysql/shrine-db-init.sql /docker-entrypoint-initdb.d
COPY hub/service/src/main/sql/hub-mysql.ddl data/shrine-setup/hub/sql/hub-mysql.ddl
