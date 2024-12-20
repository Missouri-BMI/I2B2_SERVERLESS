FROM amazonlinux:2

ARG ENVIRONMENT=dev


# Install required packages
RUN yum -y update  \
    && yum -y install \
    httpd24 \
    php \
    wget \
    && yum clean all


# Copy Webclient
COPY i2b2-webclient/ /var/www/html/admin/ 

# Copy Apache config files
COPY  /Configuration/$ENVIRONMENT/apache.conf /etc/httpd/conf.d/



# Copy the required script
COPY  /Configuration/httpd-foreground /usr/local/bin/

# File execute permissions
RUN chmod +x /usr/local/bin/httpd-foreground

# expose ports
EXPOSE 80


# Declare Volumes
VOLUME [ "/var/log/httpd", "/var/cache/httpd", "/var/run/httpd", "/var/lock/subsys"]

# Run command for starting Apache
CMD ["/bin/bash","-C", "httpd-foreground"]