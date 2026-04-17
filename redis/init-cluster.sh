#!/bin/sh
set -eu

REDIS_PASSWORD="${REDIS_PASSWORD:?REDIS_PASSWORD is required}"

wait_node() {
  host="$1"
  port="$2"
  until redis-cli -h "$host" -p "$port" -a "$REDIS_PASSWORD" ping >/dev/null 2>&1; do
    echo "[redis-cluster-init] waiting for $host:$port"
    sleep 2
  done
}

wait_node redis-node-1 7000
wait_node redis-node-2 7001
wait_node redis-node-3 7002
wait_node redis-node-4 7003
wait_node redis-node-5 7004
wait_node redis-node-6 7005

if redis-cli -h redis-node-1 -p 7000 -a "$REDIS_PASSWORD" cluster info | grep -q "cluster_state:ok"; then
  echo "[redis-cluster-init] cluster already initialized"
  exit 0
fi

echo yes | redis-cli -a "$REDIS_PASSWORD" --cluster create \
  redis-node-1:7000 \
  redis-node-2:7001 \
  redis-node-3:7002 \
  redis-node-4:7003 \
  redis-node-5:7004 \
  redis-node-6:7005 \
  --cluster-replicas 1

redis-cli -h redis-node-1 -p 7000 -a "$REDIS_PASSWORD" cluster info
