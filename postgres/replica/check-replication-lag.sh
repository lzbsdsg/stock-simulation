#!/bin/sh
set -eu

MASTER_HOST="${MASTER_HOST:-pg-master}"
MASTER_PORT="${MASTER_PORT:-5432}"
MASTER_USER="${MASTER_USER:-stock_app}"
MASTER_DB="${MASTER_DB:-stock_simulation}"
WARN_SECONDS="${WARN_SECONDS:-1}"

SQL="SELECT COALESCE(EXTRACT(EPOCH FROM (now() - pg_last_xact_replay_timestamp())), 0);"
LAG_SECONDS=$(psql -h "$MASTER_HOST" -p "$MASTER_PORT" -U "$MASTER_USER" -d "$MASTER_DB" -Atc "$SQL")

printf 'replication_lag_seconds=%s\n' "$LAG_SECONDS"

AWK_RESULT=$(awk -v lag="$LAG_SECONDS" -v warn="$WARN_SECONDS" 'BEGIN { if (lag > warn) print 1; else print 0; }')
if [ "$AWK_RESULT" -eq 1 ]; then
  echo "replication lag exceeds threshold: ${LAG_SECONDS}s > ${WARN_SECONDS}s"
  exit 1
fi
