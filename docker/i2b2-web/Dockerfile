FROM amazonlinux:2

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


# Copy Webclient
COPY i2b2-webclient-new/ /var/www/html/webclient/ 
COPY i2b2-webclient/ /var/www/html/webclient-old/ 


# Copy the required script
COPY  /Configuration/httpd-shibd-foreground /usr/local/bin/

# Copy Apache config files
COPY  /Configuration/$ENVIRONMENT/apache/i2b2.conf /etc/httpd/conf.d/
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
            /etc/shibboleth/shibd-amazon \
            /etc/shibboleth/sp-key.pem


RUN echo $'export LD_LIBRARY_PATH=/opt/shibboleth/lib64:$LD_LIBRARY_PATH\n' > /etc/sysconfig/shibd

# expose ports
EXPOSE 80

RUN ln -sf /dev/stdout /var/log/shibboleth/shibd.log

# Declare Volumes
VOLUME ["/etc/shibboleth", "/var/log/httpd", "/var/cache/shibboleth", "/var/cache/httpd", "/var/run/httpd", "/var/log/shibboleth", "/var/run/shibboleth", "/var/lock/subsys"]

# Run command for starting Apache and Shibboleth
CMD ["/bin/bash","-C", "httpd-shibd-foreground"]