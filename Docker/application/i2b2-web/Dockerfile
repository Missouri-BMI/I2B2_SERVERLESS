FROM centos:7

# default client type
ARG CLIENT_TYPE=webclient
# default server name
ARG SERVER_NAME=https://i2b2-dev.nextgenbmi.umsystem.edu/

# Update environment variable client type
ENV CLIENT_TYPE=${CLIENT_TYPE}
ENV SERVER_NAME=${SERVER_NAME}

# copy shibboleth RPM installation file
COPY /Configuration/shibboleth/shibboleth.repo /etc/yum.repos.d/

RUN yum -y update && yum clean all

RUN yum -y install \
    httpd24 \
    mod_ssl \
    php \
    wget \
    shibboleth.x86_64 && yum clean all

# Download webclient from forked repository
RUN wget https://github.com/Missouri-BMI/i2b2-webclient/archive/master.tar.gz -O /tmp/${CLIENT_TYPE}.tar.gz

# Download the source code
RUN mkdir /var/www/html/${CLIENT_TYPE} ;\
    tar --strip-components=1 -zxvf /tmp/${CLIENT_TYPE}.tar.gz -C /var/www/html/${CLIENT_TYPE}/ ;\
    rm -rf /tmp/${CLIENT_TYPE}.tar.gz

# replace the updated config file with file directory explore disable and Request Timeout set to 240 Sec
RUN rm -rf /etc/httpd/conf/httpd.conf; \
    rm -rf /etc/shibboleth/shibboleth2.xml; \
    rm -rf /etc/shibboleth/attribute-map.xml; \
    rm -rf /etc/httpd/conf.d/shib.conf

# Copy the required script
COPY /Configuration/httpd-shibd-foreground /usr/local/bin/
COPY /Configuration/environment-setup /usr/local/bin/

# Copy Apache config files
COPY /Configuration/apache/httpd.conf /etc/httpd/conf/
COPY /Configuration/apache/ajp.conf /etc/httpd/conf.d/

# Copy Shibboleth files
COPY /Configuration/shibboleth/shibboleth2.xml /etc/shibboleth/
COPY /Configuration/shibboleth/attribute-map.xml /etc/shibboleth/
COPY /Configuration/shibboleth/shib.conf /etc/httpd/conf.d/

RUN echo $'export LD_LIBRARY_PATH=/opt/shibboleth/lib64:$LD_LIBRARY_PATH\n' > /etc/sysconfig/shibd

RUN chmod +x /etc/sysconfig/shibd \
            /etc/shibboleth/shibd-redhat \
            /usr/local/bin/httpd-shibd-foreground \
            /usr/local/bin/environment-setup

RUN /usr/local/bin/environment-setup

# expose ports
EXPOSE 80


CMD ["/bin/bash","-C", "httpd-shibd-foreground"]
