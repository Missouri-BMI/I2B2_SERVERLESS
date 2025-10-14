## https://shibboleth.atlassian.net/wiki/spaces/SP3/pages/2114781232/Contributions+and+Extensions
## https://github.com/PneumaSolutions/shib-sp

FROM amazonlinux:2023

ARG ENVIRONMENT=local

# copy shibboleth RPM installation file
COPY /Configuration/Common/shibboleth/shibboleth.repo /etc/yum.repos.d/


# Base OS + httpd + shibboleth + php + wget
RUN dnf -y update && dnf -y install \
      httpd \ 
      php php-fpm \
      shibboleth.x86_64 \
      python3 python3-pip \
  && dnf clean all


# Python + pip (dnf on AL2023) — then install Supervisor in a venv
# Install Supervisor into a small venv (reliable on AL2023)
RUN python3 -m venv /opt/supervisor \
 && /opt/supervisor/bin/pip install --no-cache-dir --upgrade pip supervisor


# RUN mkdir -p /etc/supervisor /etc/supervisor/conf.d /var/log/supervisor /var/run/supervisor
COPY Configuration/Common/supervisord.conf /etc/supervisor/

# Configure php-fpm to use a Unix socket and not daemonize
RUN mkdir -p /run/php-fpm && chown apache:apache /run/php-fpm


# Copy Shibboleth files
COPY  /Configuration/Common/shibboleth/shib.conf /etc/httpd/conf.d/

# php-fpm Apache config
COPY /Configuration/Common/php-fpm.conf /etc/httpd/conf.d/

# Copy Apache config files
COPY  /Configuration/$ENVIRONMENT/apache/i2b2.conf /etc/httpd/conf.d/
COPY  /Configuration/$ENVIRONMENT/apache/ajp.conf /etc/httpd/conf.d/


# Copy Shibboleth configuration files
COPY  /Configuration/$ENVIRONMENT/shibboleth/attribute-map.xml \ 
      /Configuration/$ENVIRONMENT/shibboleth/shibboleth2.xml \
      /Configuration/$ENVIRONMENT/shibboleth/inc-md-cert-mdq.pem \
      /Configuration/$ENVIRONMENT/shibboleth/sp-cert.pem \
      /Configuration/$ENVIRONMENT/shibboleth/sp-key.pem \
    /etc/shibboleth/

# Copy Shibboleth log configuration files
COPY /Configuration/Common/shibboleth/native.logger /etc/shibboleth/
COPY /Configuration/Common/shibboleth/shibd.logger /etc/shibboleth/

# Send Apache logs to container stdout/stderr
RUN sed -i 's#CustomLog "logs/access_log"#CustomLog "/dev/stdout"#' /etc/httpd/conf/httpd.conf \
 && sed -i 's#ErrorLog "logs/error_log"#ErrorLog "/dev/stderr"#'   /etc/httpd/conf/httpd.conf 


# Simple entrypoint that starts supervisord in the foreground (PID 1)
COPY Configuration/Common/startup.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/startup.sh

# Webclient apps
COPY i2b2-webclient/     /var/www/html/webclient/
COPY i2b2-webclient-classic/         /var/www/html/webclient-classic/


# Basic healthcheck
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD curl -fsS http://127.0.0.1/ || exit 1

# Volumes (optional if you prefer file logs in addition to stdout)

VOLUME ["/tmp", "/etc/shibboleth", "/var/cache/shibboleth", "/var/cache/httpd", "/var/run/httpd", "/var/run/shibboleth", "/var/lock/subsys", "/var/run/supervisor", "/var/log/php-fpm", "/run/php-fpm"]

EXPOSE 80

# Run Supervisor (manages httpd + shibd)
CMD ["bin/bash", "-c", "/usr/local/bin/startup.sh"]