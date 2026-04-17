#!/bin/sh
set -eu

PGDATA_DIR="${PGDATA:-/var/lib/postgresql/data}"
REPL_USER="${POSTGRES_REPLICATION_USER:-repl_user}"
REPL_PASSWORD="${POSTGRES_REPLICATION_PASSWORD:?POSTGRES_REPLICATION_PASSWORD is required}"
REPL_SLOT="${REPLICATION_SLOT_NAME:-replica_slot}"
REPLICA_NAME="${REPLICA_APP_NAME:-pg-slave}"

mkdir -p "${PGDATA_DIR}"
chown -R postgres:postgres "${PGDATA_DIR}" 2>/dev/null || true
chmod 700 "${PGDATA_DIR}"

if [ ! -s "${PGDATA_DIR}/PG_VERSION" ]; then
  rm -rf "${PGDATA_DIR}"/*

  until pg_isready -h pg-master -p 5432 -U "${POSTGRES_USER:-stock_app}" >/dev/null 2>&1; do
    echo "[pg-replica] waiting for pg-master..."
    sleep 2
  done

  export PGPASSWORD="${REPL_PASSWORD}"
  pg_basebackup \
    -h pg-master \
    -p 5432 \
    -D "${PGDATA_DIR}" \
    -U "${REPL_USER}" \
    -R \
    -X stream \
    -C \
    -S "${REPL_SLOT}" \
    -P

  {
    echo "hot_standby = on"
    echo "primary_slot_name = '${REPL_SLOT}'"
    echo "primary_conninfo = 'host=pg-master port=5432 user=${REPL_USER} password=${REPL_PASSWORD} application_name=${REPLICA_NAME}'"
  } >> "${PGDATA_DIR}/postgresql.auto.conf"

  touch "${PGDATA_DIR}/standby.signal"
  chown -R postgres:postgres "${PGDATA_DIR}" 2>/dev/null || true
fi

exec docker-entrypoint.sh postgres \
  -c hot_standby=on \
  -c max_standby_streaming_delay=30s
