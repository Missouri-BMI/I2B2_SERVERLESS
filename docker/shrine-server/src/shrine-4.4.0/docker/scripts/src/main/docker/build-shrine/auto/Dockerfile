# docker build -t build-shrine-base docker/scripts/src/main/docker/build-shrine/base
# docker build -f . -t build-shrine-develop docker/scripts/src/main/docker/build-shrine/develop
# docker run  --name buildShrine -w /mnt/host/shrine -ti build-shrine-auto mvn install
#

FROM build-shrine-base
LABEL maintainer = "Harvard Catalyst Informatics"

#Hack from Marc-Danie to keep the hosted machine running after a docker run command completes - uncomment to help debug
#CMD tail -f /dev/null