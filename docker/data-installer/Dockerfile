FROM alpine:3.14

RUN apk add --no-cache \
    openjdk8 \
    apache-ant \
    wget \
    unzip \
    bash

# Download webclient from forked repository
WORKDIR /home

RUN wget https://github.com/Missouri-BMI/i2b2-data/archive/feature/snowflake.tar.gz -O /tmp/i2b2-data.tar.gz
RUN mkdir ./i2b2-data ;\
    tar --strip-components=1 -zxvf /tmp/i2b2-data.tar.gz -C ./i2b2-data ;\
    rm -rf /tmp/i2b2-data.tar.gz



COPY config/Crcdata/db.properties ./i2b2-data/edu.harvard.i2b2.data/Release_1-7/NewInstall/Crcdata/db.properties
COPY config/Hivedata/db.properties ./i2b2-data/edu.harvard.i2b2.data/Release_1-7/NewInstall/Hivedata/db.properties
COPY config/Metadata/db.properties ./i2b2-data/edu.harvard.i2b2.data/Release_1-7/NewInstall/Metadata/db.properties
COPY config/Pmdata/db.properties ./i2b2-data/edu.harvard.i2b2.data/Release_1-7/NewInstall/Pmdata/db.properties
COPY config/Workdata/db.properties ./i2b2-data/edu.harvard.i2b2.data/Release_1-7/NewInstall/Workdata/db.properties


COPY /install-i2b2-data ./

RUN chmod -v +x /install-i2b2-data

CMD ["/bin/bash", "-C", "./install-i2b2-data"]

