# Common base Dockerfile that sets the parts to build shrine
#
# Use this file as the base piece for Dockerfiles for specific environments. Build this first.
#
# from the top level of the shrine repo
# docker build -t build-shrine-base docker/scripts/src/main/docker/build-shrine/base
#
#Begin with an OS with the zulu 17 JVM
#FROM azul/zulu-openjdk-centos:17
FROM azul/zulu-openjdk:17.0.9-17.46-arm64
LABEL maintainer = "Harvard Catalyst Informatics"

RUN yum update -y

#install maven
RUN yum install -y wget
RUN wget http://repos.fedorapeople.org/repos/dchen/apache-maven/epel-apache-maven.repo -O /etc/yum.repos.d/epel-apache-maven.repo
RUN yum install -y apache-maven

#install the node parts
RUN curl -sL https://rpm.nodesource.com/setup_12.x | bash -
RUN yum install -y nodejs
RUN yum install -y bzip2
RUN echo '{ "allow_root": true }' > ~/.bowerrc

# make a mount point for the host's source code
RUN mkdir -p -v /mnt/host/shrine