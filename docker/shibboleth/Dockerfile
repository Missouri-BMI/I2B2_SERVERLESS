FROM amazonlinux:2

ARG ENVIRONMENT=dev

# copy shibboleth RPM installation file
COPY /Configuration/shibboleth.repo /etc/yum.repos.d/

# Install required packages
RUN yum -y update  \
    && yum -y install \
    httpd24 \
    php \
    shibboleth.x86_64  \
    && yum clean all


# Copy the required script
COPY  /Configuration/shibd-foreground /usr/local/bin/

# Copy Apache config files
COPY  /Configuration/$ENVIRONMENT/apache/sp.conf /etc/httpd/conf.d/


COPY  /Configuration/$ENVIRONMENT/shibboleth/attribute-map.xml \ 
      /Configuration/$ENVIRONMENT/shibboleth/shibboleth2.xml \
      /Configuration/$ENVIRONMENT/shibboleth/inc-md-cert-mdq.pem \
      /Configuration/$ENVIRONMENT/shibboleth/sp-cert.pem \
      /Configuration/$ENVIRONMENT/shibboleth/sp-key.pem \
      /etc/shibboleth/


# File execute permissions
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