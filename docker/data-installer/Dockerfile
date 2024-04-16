FROM alpine:3.14

RUN apk add \
    openjdk8 \
    apache-ant \
    wget \
    unzip \
    bash

# Download webclient from forked repository
WORKDIR /home

COPY /i2b2-data/  ./i2b2-data/

COPY /config/ ./config/


COPY /scripts/install-i2b2-data ./

RUN chmod -v +x ./install-i2b2-data

CMD ["/bin/bash", "-C", "./install-i2b2-data"]

