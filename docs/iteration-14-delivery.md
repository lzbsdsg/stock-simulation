# Iteration 14 交付说明（部署：Docker集群 + Nginx LB + PG主从 + Redis Cluster）

## 1. 迭代目标

基于 [docs/doc-D-dev-roadmap.md](docs/doc-D-dev-roadmap.md) 的 Iteration 14（Week 15）：

- 完成生产级容器化部署编排（2+App 实例）
- 提供 Nginx 入口（负载均衡、限流、WS 粘性、SSL 终止）
- 完成 PostgreSQL 主从复制与 Redis Cluster 初始化能力
- 完成生产配置（主从数据源 + Redis Cluster 节点）
- 提供健康检查与部署验收命令

## 2. 本次完成项

### 2.1 生产 Docker 构建链路

- 升级 [Dockerfile](Dockerfile) 为多目标构建：
  - `backend-builder` / `backend-runtime`：后端多阶段构建
  - `frontend-builder` / `frontend-nginx`：前端构建并打包到 Nginx
- 新增 [.dockerignore](.dockerignore) 减少构建上下文体积，提升构建效率与稳定性。

### 2.2 生产 docker-compose 拓扑（高可用版）

- 重构 [docker-compose.yml](docker-compose.yml)，完成以下服务编排：
  - `nginx`
  - `app-1`, `app-2`
  - `pg-master`, `pg-slave-1`, `pg-slave-2`
  - `redis-node-1` ~ `redis-node-6`, `redis-cluster-init`
  - `rabbitmq`
  - `prometheus`, `grafana`, `loki`
- 增加关键 `depends_on` 与 `healthcheck`，保障启动顺序和基础可用性。

### 2.3 Nginx 生产配置

- 更新 [nginx/nginx.conf](nginx/nginx.conf)：
  - `limit_req_zone` 全局限流
  - `upstream app_backend` 负载均衡（2实例）
  - `upstream ws_backend` + `ip_hash`（WS 粘性会话）
  - 静态资源 `gzip` 与 `cache-control`
  - HTTPS 终止（Let's Encrypt 证书挂载路径）
- 镜像构建阶段默认生成自签名证书，确保 `docker compose up -d` 可直接启动；生产可替换为 Let's Encrypt 证书。
- 新增 [nginx/certs/README.md](nginx/certs/README.md) 说明证书落盘要求。

### 2.4 PostgreSQL 主从复制与脚本

- 新增主库初始化脚本 [postgres/master/init/01-create-replication-user.sh](postgres/master/init/01-create-replication-user.sh)：
  - 初始化复制账号（幂等创建/更新）
- 新增从库初始化脚本 [postgres/replica/init-replica.sh](postgres/replica/init-replica.sh)：
  - `pg_basebackup -R` 拉取基线
  - 生成 `standby.signal`
  - 写入 `primary_conninfo` 与复制槽参数
- 新增复制延迟检测脚本 [postgres/replica/check-replication-lag.sh](postgres/replica/check-replication-lag.sh)。

### 2.5 Redis Cluster 初始化

- 新增 [redis/init-cluster.sh](redis/init-cluster.sh)：
  - 启动等待 6 节点可用
  - 执行 `redis-cli --cluster create`
  - 支持已初始化场景幂等退出
- `docker-compose.yml` 中由 `redis-cluster-init` 一次性任务触发初始化。

### 2.6 生产应用配置

- 更新 [src/main/resources/application-prod.yml](src/main/resources/application-prod.yml)：
  - 增加 `spring.datasource.master/slave`（主从数据源）
  - 增加 `spring.data.redis.cluster.nodes`（Redis Cluster 节点）
  - 保留 RabbitMQ 生产配置
  - 增强 `management.endpoint.health`：展示组件详情，覆盖 DB/Redis/Rabbit

### 2.7 监控抓取目标对齐

- 更新 [prometheus/prometheus.yml](prometheus/prometheus.yml)：
  - 抓取目标改为 `app-1:8081`、`app-2:8081`
  - 增加 `prometheus` 自监控目标
  - 保留 RabbitMQ 指标抓取端口 `15692`

### 2.8 环境变量模板

- 更新 [.env.example](.env.example)：
  - 补齐 Iteration 14 所需变量：主从 DB、Redis Cluster、复制账号、Grafana 管理员等。

## 3. 与路线图任务对齐

- 生产 Dockerfile（后端多阶段 + 前端 Nginx）：已完成
- 高并发架构 docker-compose.yml：已完成
- 生产版 nginx.conf（限流/LB/ip_hash/gzip/cache/SSL）：已完成
- PG 主从脚本（含 standby.signal）与复制延迟检测：已完成
- Redis Cluster 初始化脚本：已完成
- application-prod.yml 主从与集群配置：已完成
- `/actuator/health` 组件健康检查：已完成

## 4. 已执行校验

1. Compose 配置渲染校验

```powershell
Set-Location "d:\StockSimulation\stock-simulation"
docker compose -f docker-compose.yml config
```

结果：通过（配置可解析，返回 `compose-config-ok`）。

2. 关键脚本内容复核

- [postgres/replica/init-replica.sh](postgres/replica/init-replica.sh)
- [redis/init-cluster.sh](redis/init-cluster.sh)

结果：脚本完整，包含主从/集群初始化关键命令。

## 5. 验收命令（建议按顺序执行）

1. 启动全部服务

```powershell
Set-Location "d:\StockSimulation\stock-simulation"
docker compose up -d
```

2. 检查服务状态

```powershell
docker compose ps
```

3. 验证 Redis Cluster 状态

```powershell
docker compose exec redis-node-1 redis-cli -p 7000 -a $env:REDIS_PASSWORD cluster info
```

通过标准：包含 `cluster_state:ok`。

4. 验证应用健康检查（DB/Redis/MQ）

```powershell
curl -k https://localhost/actuator/health
```

通过标准：返回 JSON 包含 `components`，且可见 `db`、`redis`、`rabbit`。

5. 验证 Nginx 对 2 个应用实例的负载

```powershell
curl -k https://localhost/actuator/health
curl -k https://localhost/actuator/health
```

可结合容器日志确认请求分发到 `app-1` 与 `app-2`。

6. 验证故障切换

```powershell
docker compose stop app-1
curl -k https://localhost/actuator/health
```

通过标准：请求仍成功，由 `app-2` 承载。

## 6. 文档同步

- 已在 [docs/doc-D-dev-roadmap.md](docs/doc-D-dev-roadmap.md) 将 Iteration 14 标记为完成，并关联本交付文档。
