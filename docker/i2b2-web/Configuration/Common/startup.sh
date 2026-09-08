#!/bin/bash
set -euo pipefail

# The root filesystem is read-only (ReadonlyRootFilesystem: true); the only writable paths are
# the Dockerfile VOLUMEs (/run, /var/cache, /var/log, /var/lib/php, ...), which mount EMPTY.
# Recreate the per-service runtime dirs on them (this runs as root at start, so it can) before
# launching shibd / php-fpm / httpd.
mkdir -p /run/httpd /run/php-fpm /run/shibboleth \
         /var/cache/httpd /var/cache/php-fpm /var/cache/shibboleth \
         /var/log/httpd /var/log/php-fpm \
         /var/lib/php/session /var/lib/php/wsdlcache
chown -R apache:apache /run/php-fpm /var/cache/php-fpm /var/log/php-fpm \
                       /var/lib/php/session /var/lib/php/wsdlcache

# Start Shibboleth daemon
/usr/sbin/shibd -f -F &

# Start PHP-FPM
php-fpm --nodaemonize &

# Start Apache in foreground
exec httpd -DFOREGROUND