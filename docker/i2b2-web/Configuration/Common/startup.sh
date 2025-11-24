#!/bin/bash
set -euo pipefail

# In read-only mode, we assume /run/php-fpm is already a writable tmpfs
# Start Shibboleth daemon
/usr/sbin/shibd -f -F &

# Start PHP-FPM
php-fpm --nodaemonize &

# Start Apache in foreground
exec httpd -DFOREGROUND