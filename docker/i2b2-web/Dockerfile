FROM centos:7

ARG ENVIRONMENT=local
ARG BRANCH=feature/unistore


# Install required packages
RUN yum -y update  \
    && yum -y install \
    httpd24 \
    php \
    wget \
    && yum clean all

# Download webclient from forked repository
RUN wget https://github.com/Missouri-BMI/i2b2-webclient/archive/$BRANCH.tar.gz -O /tmp/webclient.tar.gz \
    && mkdir /var/www/html/webclient \
    && tar --strip-components=1 -zxvf /tmp/webclient.tar.gz -C /var/www/html/webclient/ \
    && rm -rf /tmp/webclient.tar.gz

# Copy the required script
COPY  /Configuration/httpd-shibd-foreground /usr/local/bin/
COPY  /Configuration/environment-setup /usr/local/bin/

# Copy Apache config files
COPY  /Configuration/$ENVIRONMENT/apache/httpd.conf /etc/httpd/conf/
COPY  /Configuration/$ENVIRONMENT/apache/ajp.conf /etc/httpd/conf.d/

# File execute permissions
RUN chmod +x /usr/local/bin/httpd-shibd-foreground \
            /usr/local/bin/environment-setup


RUN /usr/local/bin/environment-setup

# expose ports
EXPOSE 80

# Declare Volumes
# VOLUME [ "/var/log/httpd", "/var/cache/httpd", "/var/run/httpd", "/var/lock/subsys"]

# Run command for starting Apache
CMD ["/bin/bash","-C", "httpd-shibd-foreground"]