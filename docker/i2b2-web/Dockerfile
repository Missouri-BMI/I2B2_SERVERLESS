FROM amazonlinux:2023

# copy shibboleth RPM installation file
COPY /Configuration/Common/shibboleth/shibboleth.repo /etc/yum.repos.d/

RUN dnf -y update && dnf -y install \
      httpd \
      php php-fpm php-soap php-xml php-mbstring \
      shibboleth.x86_64 \
      wget unzip tar gzip \
  && dnf clean all && rm -rf /var/cache/dnf

# Common (non-secret) config
COPY /Configuration/Common/www.conf /etc/php-fpm.d/www.conf
COPY /Configuration/Common/php-fpm.conf /etc/httpd/conf.d/
COPY /Configuration/Common/shibboleth/native.logger /etc/shibboleth/
COPY /Configuration/Common/shibboleth/shibd.logger  /etc/shibboleth/

# THIS variant's Apache + Shibboleth config, baked to its final paths.
ARG ENVIRONMENT=dev
ARG TARGET=mu
COPY Configuration/${ENVIRONMENT}/${TARGET}/apache/i2b2.conf /etc/httpd/conf.d/i2b2.conf
COPY Configuration/${ENVIRONMENT}/${TARGET}/apache/ajp.conf  /etc/httpd/conf.d/ajp.conf
COPY Configuration/${ENVIRONMENT}/${TARGET}/apache/sp.conf   /etc/httpd/conf.d/sp.conf
COPY Configuration/${ENVIRONMENT}/${TARGET}/shibboleth/ /etc/shibboleth/

# Send Apache logs to container stdout/stderr
RUN sed -i 's#CustomLog "logs/access_log"#CustomLog "/dev/stdout"#' /etc/httpd/conf/httpd.conf \
 && sed -i 's#ErrorLog "logs/error_log"#ErrorLog "/dev/stderr"#'   /etc/httpd/conf/httpd.conf

# Webclient apps
COPY i2b2-webclient/ /var/www/html/webclient/

# Stage the webclient-classic variants, then symlink the one for THIS target into the docroot.
RUN set -eux; \
    for spec in "release/mu:mu" "release/mu-admin:mu-admin" "release/washu-admin:washu-admin"; do \
      BRANCH="${spec%%:*}"; DIR="${spec##*:}"; \
      wget "https://github.com/Missouri-BMI/i2b2-webclient-classic/archive/refs/heads/${BRANCH}.zip" -O /tmp/wc.zip; \
      rm -rf /tmp/wc; mkdir -p /tmp/wc "/opt/webclient/${DIR}"; \
      unzip -q /tmp/wc.zip -d /tmp/wc; \
      cp -a /tmp/wc/i2b2-webclient-classic-*/. "/opt/webclient/${DIR}/"; \
      rm -rf /tmp/wc /tmp/wc.zip; \
    done
RUN case "${TARGET}" in \
      mu)           ln -sfn /opt/webclient/mu          /var/www/html/webclient-classic ;; \
      mu-shrine)    ln -sfn /opt/webclient/mu-admin    /var/www/html/admin ;; \
      washu-shrine) ln -sfn /opt/webclient/washu-admin /var/www/html/admin ;; \
      *)            ln -sfn /opt/webclient/mu          /var/www/html/webclient-classic ;; \
    esac

# Startup script (starts shibd + php-fpm + httpd)
COPY Configuration/Common/startup.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/startup.sh

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD curl -fsS http://127.0.0.1/health || exit 1


VOLUME ["/tmp", "/run", "/var/cache", "/var/log", "/var/lib/php", "/var/lock"]

EXPOSE 80
CMD ["/usr/local/bin/startup.sh"]
