FROM amazonlinux:2

ARG ENVIRONMENT=dev
ARG PROJECT=mu
ARG BRANCH=master


# copy shibboleth RPM installation file
COPY /Configuration/shibboleth.repo /etc/yum.repos.d/

# Install required packages
RUN yum -y update  \
    && yum -y install \
    httpd24 \
    php \
    wget \
    unzip \
    shibboleth.x86_64  \
    && yum clean all

# Download and extract the i2b2-webclient from GitHub
RUN wget https://github.com/Missouri-BMI/i2b2-webclient/archive/refs/heads/$BRANCH.zip -O /tmp/i2b2-webclient.zip \
    && unzip  /tmp/i2b2-webclient.zip -d /var/www/html/ \
    && rm /tmp/i2b2-webclient.zip \
    && mv /var/www/html/i2b2-webclient-* /var/www/html/admin
    
# Copy the required script
COPY  /Configuration/shibd-foreground /usr/local/bin/

# Copy Apache config files
COPY  /Configuration/$ENVIRONMENT/$PROJECT/apache/sp.conf /etc/httpd/conf.d/


COPY  /Configuration/$ENVIRONMENT/$PROJECT/shibboleth/attribute-map.xml \ 
      /Configuration/$ENVIRONMENT/$PROJECT/shibboleth/shibboleth2.xml \
      /Configuration/$ENVIRONMENT/$PROJECT/shibboleth/inc-md-cert-mdq.pem \
      /Configuration/$ENVIRONMENT/$PROJECT/shibboleth/sp-cert.pem \
      /Configuration/$ENVIRONMENT/$PROJECT/shibboleth/sp-key.pem \
      /etc/shibboleth/


# File execute permissions
RUN chmod +x /usr/local/bin/shibd-foreground
RUN chmod +x /usr/local/bin/shibd-foreground \
            /etc/shibboleth/shibd-amazon \
            /etc/shibboleth/sp-key.pem

        
RUN echo $'export LD_LIBRARY_PATH=/opt/shibboleth/lib64:$LD_LIBRARY_PATH\n' > /etc/sysconfig/shibd


# expose ports
EXPOSE 80

RUN ln -sf /dev/stdout /var/log/shibboleth/shibd.log


HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD curl --fail http://localhost/health || exit 1

# Declare Volumes
VOLUME ["/etc/shibboleth", "/var/log/httpd", "/var/cache/shibboleth", "/var/cache/httpd", "/var/run/httpd", "/var/log/shibboleth", "/var/run/shibboleth", "/var/lock/subsys"]

# Run command for starting Apache and Shibboleth
CMD ["/bin/bash","-C", "shibd-foreground"]