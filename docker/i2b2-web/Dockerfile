FROM centos:7

ARG ENVIRONMENT=local
ARG BRANCH=feature/mu

# copy shibboleth RPM installation file
COPY /Configuration/shibboleth.repo /etc/yum.repos.d/

# Install required packages
RUN yum -y update  \
    && yum -y install \
    httpd24 \
    php \
    wget \
    shibboleth.x86_64  \
    && yum clean all

# Download webclient from forked repository
RUN wget https://github.com/Missouri-BMI/i2b2-webclient-new/archive/$BRANCH.tar.gz -O /tmp/webclient.tar.gz \
    && mkdir /var/www/html/webclient \
    && tar --strip-components=1 -zxvf /tmp/webclient.tar.gz -C /var/www/html/webclient/ \
    && rm -rf /tmp/webclient.tar.gz


# Copy the required script
COPY  /Configuration/httpd-shibd-foreground /usr/local/bin/
COPY  /Configuration/environment-setup /usr/local/bin/

# Copy Apache config files
COPY  /Configuration/$ENVIRONMENT/apache/httpd.conf /etc/httpd/conf/
COPY  /Configuration/$ENVIRONMENT/apache/ajp.conf /etc/httpd/conf.d/

# Copy Shibboleth files
COPY  /Configuration/$ENVIRONMENT/shibboleth/shib.conf /etc/httpd/conf.d/

COPY  /Configuration/$ENVIRONMENT/shibboleth/attribute-map.xml \ 
      /Configuration/$ENVIRONMENT/shibboleth/shibboleth2.xml \
      /Configuration/$ENVIRONMENT/shibboleth/inc-md-cert-mdq.pem \
      /Configuration/$ENVIRONMENT/shibboleth/sp-cert.pem \
      /Configuration/$ENVIRONMENT/shibboleth/sp-key.pem \
    /etc/shibboleth/


# File execute permissions
RUN chmod +x /usr/local/bin/httpd-shibd-foreground \
            /etc/shibboleth/shibd-redhat \
            /usr/local/bin/environment-setup

RUN chmod 755 /etc/shibboleth/sp-key.pem

RUN /usr/local/bin/environment-setup

RUN echo $'export LD_LIBRARY_PATH=/opt/shibboleth/lib64:$LD_LIBRARY_PATH\n' > /etc/sysconfig/shibd

# expose ports
EXPOSE 80

# Declare Volumes
VOLUME ["/etc/shibboleth", "/var/log/httpd", "/var/cache/shibboleth", "/var/cache/httpd", "/var/run/httpd", "/var/log/shibboleth", "/var/run/shibboleth", "/var/lock/subsys"]

# Run command for starting Apache and Shibboleth
CMD ["/bin/bash","-C", "httpd-shibd-foreground"]