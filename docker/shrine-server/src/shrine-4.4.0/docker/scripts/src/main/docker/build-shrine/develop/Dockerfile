# Set up an image for building on demand
#
# docker build -t build-shrine-base docker/scripts/src/main/docker/build-shrine/base
# docker build -t build-shrine-develop docker/scripts/src/main/docker/build-shrine/develop
# docker run  --name buildShrine -v ~/.m2:/root/.m2:cached -v ~/.npm:/root/.npm:cached -ti build-shrine-develop /bin/bash

FROM build-shrine-base
LABEL maintainer = "Harvard Catalyst Informatics"

RUN yum install -y emacs

# make mount points for maven and npm caches from the host
RUN mkdir -p -v /root/.m2
RUN mkdir -p -v /root/.npm

#Hack from Marc-Danie to keep the hosted machine running after a docker run command
CMD tail -f /dev/null