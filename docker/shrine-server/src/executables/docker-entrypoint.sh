#!/bin/bash
# SHRINE container entrypoint.
#
# Before Tomcat starts, guarantee the SHRINE 4.4.1 databases exist and their
# schemas are loaded:
#   CREATE DATABASE ADAPTER;  + shrine-setup/adapter/sql/adapter-mysql.ddl
#   CREATE DATABASE QEP;      + shrine-setup/qep/sql/qep-mysql.ddl
#
# Connection details (host/port/db/user/password) are read from the jdbc/adapter
# and jdbc/qep datasources in conf/context.xml so there is a single source of
# truth per (environment, project) build. Idempotent: existing schemas are
# left untouched, so it is safe to run on every container/task start.
set -euo pipefail

CONTEXT_XML="${CATALINA_HOME}/conf/context.xml"
DDL_DIR="${SHRINE_SETUP_DIR:-${CATALINA_HOME}/lib/shrine-setup}"
export MYSQL_HISTFILE=/dev/null   # avoid writes to a read-only root filesystem

MYSQL_BIN="$(command -v mysql || command -v mariadb || true)"
if [ -z "$MYSQL_BIN" ]; then
  echo "[entrypoint] ERROR: no mysql/mariadb client on PATH" >&2
  exit 1
fi

# Pull an attribute (url|username|password) out of a <Resource name="jdbc/NAME" .../> block.
resource_attr() {
  awk -v rn="name=\"$1\"" '
    index($0, rn) { inblock=1 }
    inblock       { buf = buf " " $0 }
    inblock && /\/>/ { print buf; exit }
  ' "$CONTEXT_XML" | grep -oE "$2=\"[^\"]*\"" | head -1 | sed -E "s/^$2=\"(.*)\"\$/\1/"
}

# jdbc:mariadb://HOST:PORT/DBNAME?params -> components
jdbc_host() { local u="${1#*://}"; u="${u%%/*}"; echo "${u%%:*}"; }
jdbc_port() { local u="${1#*://}"; u="${u%%/*}"; local p="${u##*:}"; [ "$p" = "$u" ] && echo 3306 || echo "$p"; }
jdbc_db()   { local u="${1#*://}"; u="${u#*/}"; echo "${u%%\?*}"; }

ADAPTER_URL="$(resource_attr 'jdbc/adapter' 'url')"
QEP_URL="$(resource_attr 'jdbc/qep' 'url')"
DB_USER="$(resource_attr 'jdbc/adapter' 'username')"
DB_PASS="$(resource_attr 'jdbc/adapter' 'password')"

if [ -z "$ADAPTER_URL" ] || [ -z "$QEP_URL" ]; then
  echo "[entrypoint] ERROR: jdbc/adapter or jdbc/qep datasource not found in $CONTEXT_XML" >&2
  exit 1
fi

DB_HOST="$(jdbc_host "$ADAPTER_URL")"
DB_PORT="$(jdbc_port "$ADAPTER_URL")"
ADAPTER_DB="$(jdbc_db "$ADAPTER_URL")"
QEP_DB="$(jdbc_db "$QEP_URL")"

myq() { "$MYSQL_BIN" --connect-timeout=5 -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASS" "$@"; }

echo "[entrypoint] db=${DB_HOST}:${DB_PORT} user=${DB_USER} adapter=${ADAPTER_DB} qep=${QEP_DB}"

# Wait for the database to accept connections (RDS/compose may still be starting).
for i in $(seq 1 60); do
  if myq -e "SELECT 1" >/dev/null 2>&1; then break; fi
  echo "[entrypoint] waiting for database ${DB_HOST}:${DB_PORT} (${i}/60)..."
  sleep 5
done

# ensure_db <dbname> <ddl file> <sentinel table>
ensure_db() {
  local db="$1" ddl="$2" sentinel="$3" n
  myq -e "CREATE DATABASE IF NOT EXISTS \`${db}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
  n="$(myq -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${db}' AND table_name='${sentinel}';")"
  if [ "$n" = "0" ]; then
    if [ -f "$ddl" ]; then
      echo "[entrypoint] loading schema into ${db} from ${ddl}"
      myq "$db" < "$ddl"
    else
      echo "[entrypoint] WARNING: DDL ${ddl} not found; ${db} left empty" >&2
    fi
  else
    echo "[entrypoint] ${db} already initialized; skipping DDL"
  fi
}

ensure_db "$ADAPTER_DB" "${DDL_DIR}/adapter/sql/adapter-mysql.ddl" "ADAPTER_MAPPING"
ensure_db "$QEP_DB"     "${DDL_DIR}/qep/sql/qep-mysql.ddl"         "QUERY_SENT"

echo "[entrypoint] databases ready; starting Tomcat"
exec "$@"
