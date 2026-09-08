FROM emberstack/openssl:latest

LABEL maintainer = "Harvard Catalyst Informatics"
#ITEAM NOTE - this is where context files should be on all our stacks
#NOTE: /usr/lib/ssl/misc/CA.pl

COPY docker/scripts/src/main/docker/images/certs/setup_shrine_keystore.sh /tmp/setup_shrine_keystore.sh
RUN chmod +x /tmp/setup_shrine_keystore.sh

RUN apk add --no-cache openjdk11
RUN apk add --no-cache bash


