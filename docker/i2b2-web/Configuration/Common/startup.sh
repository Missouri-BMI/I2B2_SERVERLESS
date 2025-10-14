#!/usr/bin/env bash
set -euo pipefail


echo "[entrypoint] starting supervisord (shibd + httpd)"
exec /opt/supervisor/bin/supervisord -n -c /etc/supervisor/supervisord.conf
