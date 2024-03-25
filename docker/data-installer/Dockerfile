FROM alpine:3.14

RUN apk add \
    openjdk8 \
    apache-ant \
    wget \
    unzip \
    bash

# Download webclient from forked repository
WORKDIR /home

# RUN wget https://github.com/Missouri-BMI/i2b2-data/archive/feature/new-ont.tar.gz -O /tmp/i2b2-data.tar.gz
# RUN mkdir ./i2b2-data ;\
#     tar --strip-components=1 -zxvf /tmp/i2b2-data.tar.gz -C ./i2b2-data ;\
#     rm -rf /tmp/i2b2-data.tar.gz
# COPY /i2b2-data/  ./i2b2-data/

COPY /config/ ./config/


COPY /scripts/install-i2b2-data ./

RUN chmod -v +x ./install-i2b2-data

CMD ["/bin/bash", "-C", "./install-i2b2-data"]

