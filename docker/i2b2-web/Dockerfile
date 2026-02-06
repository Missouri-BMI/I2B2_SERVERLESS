## https://shibboleth.atlassian.net/wiki/spaces/SP3/pages/2114781232/Contributions+and+Extensions
## https://github.com/PneumaSolutions/shib-sp

FROM amazonlinux:2023

## [dev | prod]
ARG ENVIRONMENT=dev 

## [mu | mu-shrine | washu-shrine]
ARG TARGET=mu 

# copy shibboleth RPM installation file
COPY /Configuration/Common/shibboleth/shibboleth.repo /etc/yum.repos.d/

# Base OS + httpd + shibboleth + php + wget
RUN dnf -y update && dnf -y install \
      httpd \ 
      php php-fpm php-soap php-xml php-mbstring \
      shibboleth.x86_64 \
      wget unzip \
  && dnf clean all


COPY /Configuration/Common/www.conf /etc/php-fpm.d/www.conf


# php-fpm Apache config
COPY /Configuration/Common/php-fpm.conf /etc/httpd/conf.d/

# Copy Apache config files
COPY  /Configuration/$ENVIRONMENT/$TARGET/apache/i2b2.conf /etc/httpd/conf.d/
COPY  /Configuration/$ENVIRONMENT/$TARGET/apache/ajp.conf /etc/httpd/conf.d/
COPY  /Configuration/$ENVIRONMENT/$TARGET/apache/sp.conf /etc/httpd/conf.d/


# Copy Shibboleth configuration files
COPY  /Configuration/$ENVIRONMENT/$TARGET/shibboleth/attribute-map.xml \ 
      /Configuration/$ENVIRONMENT/$TARGET/shibboleth/shibboleth2.xml \
      /Configuration/$ENVIRONMENT/$TARGET/shibboleth/inc-md-cert-mdq.pem \
      /Configuration/$ENVIRONMENT/$TARGET/shibboleth/sp-cert.pem \
      /Configuration/$ENVIRONMENT/$TARGET/shibboleth/sp-key.pem \
    /etc/shibboleth/

# Copy Shibboleth log configuration files
COPY /Configuration/Common/shibboleth/native.logger /etc/shibboleth/
COPY /Configuration/Common/shibboleth/shibd.logger /etc/shibboleth/

# Send Apache logs to container stdout/stderr
RUN sed -i 's#CustomLog "logs/access_log"#CustomLog "/dev/stdout"#' /etc/httpd/conf/httpd.conf \
 && sed -i 's#ErrorLog "logs/error_log"#ErrorLog "/dev/stderr"#'   /etc/httpd/conf/httpd.conf 


# Webclient apps
COPY i2b2-webclient/     /var/www/html/webclient/


# Download and extract the i2b2-webclient-classic from GitHub based on environment
RUN CLIENT_PATH="webclient-classic"; \
    BRANCH="release/mu"; \
    if [ "$TARGET" = "mu-shrine" ]; then \
      BRANCH="release/mu-admin"; \
      CLIENT_PATH="admin"; \
    elif [ "$TARGET" = "washu-shrine" ]; then \
      BRANCH="release/washu-admin"; \
      CLIENT_PATH="admin"; \
    fi && \
    wget https://github.com/Missouri-BMI/i2b2-webclient-classic/archive/refs/heads/$BRANCH.zip -O /tmp/i2b2-webclient.zip && \
    unzip /tmp/i2b2-webclient.zip -d /var/www/html/ && \
    rm /tmp/i2b2-webclient.zip && \
    mv /var/www/html/i2b2-webclient-* /var/www/html/$CLIENT_PATH

# Simple entrypoint that starts supervisord in the foreground (PID 1)
COPY Configuration/Common/startup.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/startup.sh

# Basic healthcheck
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD curl -fsS http://127.0.0.1/health || exit 1

# Volumes (optional if you prefer file logs in addition to stdout)

VOLUME ["/tmp", "/etc/shibboleth", "/var/cache/shibboleth", "/var/cache/httpd", "/var/run/httpd", "/var/run/shibboleth", "/var/lock/subsys", "/var/run/supervisor", "/var/log/php-fpm", "/run/php-fpm"]

EXPOSE 80

# Pre-create all necessary directories for services to write to
RUN mkdir -p /run/httpd /run/php-fpm /run/shibboleth \
             /var/cache/httpd /var/cache/php-fpm /var/cache/shibboleth \
             /var/log/httpd /var/log/php-fpm \
             /var/lib/php/session /var/lib/php/wsdlcache \
  && chown -R apache:apache /run/httpd /run/php-fpm /run/shibboleth \
                            /var/cache/httpd /var/cache/php-fpm /var/cache/shibboleth \
                            /var/log/httpd /var/log/php-fpm \
                            /var/lib/php/session /var/lib/php/wsdlcache

CMD ["bin/bash", "-c", "/usr/local/bin/startup.sh"]