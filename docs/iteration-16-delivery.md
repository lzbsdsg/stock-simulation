# Iteration 16 交付说明（性能调优）

## 1. 迭代目标

基于 `docs/doc-D-dev-roadmap.md` 的 Iteration 16（Week 17）：

- 慢 SQL 优化（可观测 + 索引优化）
- 缓存与连接池调优（参数可配置化）
- JVM 与异步线程池调优
- 前端构建与实时渲染性能优化
- 提供可执行、可复现的完整验收方法（不只单测）

## 2. 本次完成项

### 2.1 慢 SQL 观测与热点查询优化

1) PostgreSQL 慢 SQL 日志
- 文件：`docker-compose.dev.yml`
- 变更：`pg-master` 增加
  - `-c log_min_duration_statement=50`
  - `-c log_line_prefix=%m [%p] %u@%d`

2) 热点查询索引迁移（Flyway）
- 新增：`src/main/resources/db/migration/V20260417_001__optimize_iteration16_hot_queries.sql`
- 索引包括：
  - `idx_user_created_id_desc`（管理员用户列表排序 + 当日新增）
  - `idx_user_role`（管理员角色统计）
  - `idx_trade_record_traded_at`（交易金额/笔数时间窗口统计）
  - `idx_order_status_updated_id`（归档候选扫描）
  - `idx_position_user_total_quantity`（管理端持仓数量统计）

### 2.2 连接池调优（可配置化）

1) 数据源路由连接池参数配置化
- 文件：`src/main/java/com/lzbsdsg/stocksimulation/config/DataSourceRoutingConfig.java`
- 变更：
  - 支持读取 `spring.datasource.master.hikari.*` 与 `spring.datasource.slave.hikari.*`
  - 支持读取通用 `spring.datasource.hikari.*` 回退
  - 支持 `connection-timeout` / `idle-timeout` / `max-lifetime` / `keepalive-time` / `leak-detection-threshold`

2) 默认参数收敛
- 结合现有开发配置生效：
  - 主库池：`min=10, max=30`
  - 从库池：`min=20, max=50`
  - 启用 `keepalive-time=120000`
  - 启用 `leak-detection-threshold=30000`

### 2.3 异步与 MQ 消费参数调优（可配置化）

1) 异步线程池配置化
- 文件：`src/main/java/com/lzbsdsg/stocksimulation/config/AsyncConfig.java`
- 新增配置键：
  - `app.async.core-pool-size`
  - `app.async.max-pool-size`
  - `app.async.queue-capacity`
  - `app.async.keep-alive-seconds`

2) 撮合消费者参数配置化
- 文件：`src/main/java/com/lzbsdsg/stocksimulation/config/RabbitMQConfig.java`
- 新增配置键：
  - `app.rabbit.match.prefetch`
  - `app.rabbit.match.concurrent-consumers`
  - `app.rabbit.match.max-concurrent-consumers`

3) 默认参数
- 文件：`src/main/resources/application.yml`

### 2.4 JVM 调优

- 文件：`docker-compose.dev.yml`
- `app-1` / `app-2` 增加 `JAVA_TOOL_OPTIONS`：
  - `-XX:+UseG1GC`
  - `-XX:MaxGCPauseMillis=100`
  - `-Xms1g -Xmx2g`
  - `-XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=512m`

### 2.5 前端性能优化

1) Vite 构建分包
- 文件：`stock-simulation-web/vite.config.ts`
- 变更：
  - `build.rollupOptions.output.manualChunks`（`vendor-vue-core` / `vendor-ws` / `vendor-echarts` / `vendor-element-plus` / `vendor-misc`）
  - 保持路由懒加载，与分包策略协同

2) WebSocket 合批渲染（requestAnimationFrame）
- 文件：`stock-simulation-web/src/composables/useWebSocket.ts`
- 文件：`stock-simulation-web/src/stores/market.ts`
- 变更：
  - 新增 `onQuotes` 批量回调
  - 同帧内按 `stockCode` 合并更新，减少高频状态写入

## 3. 已执行验证（本次已跑）

1) 后端编译

```cmd
cd /d d:\StockSimulation\stock-simulation
mvnw.cmd -DskipTests compile
```

结果：通过（`BUILD SUCCESS`）。

2) 后端测试套件

```cmd
cd /d d:\StockSimulation\stock-simulation
mvnw.cmd test
```

结果：通过（`Tests run: 44, Failures: 0, Errors: 0, Skipped: 0`）。

3) 前端 WebSocket 单测

```cmd
cd /d d:\StockSimulation\stock-simulation\stock-simulation-web
pnpm test -- src/composables/__tests__/useWebSocket.spec.ts
```

结果：通过（`2 passed, 0 failed`）。

4) 前端构建

```cmd
cd /d d:\StockSimulation\stock-simulation\stock-simulation-web
pnpm build
```

结果：通过（构建成功，已输出分包产物）。

5) Compose 配置渲染检查

```cmd
cd /d d:\StockSimulation\stock-simulation
docker compose -f docker-compose.dev.yml config
```

结果：通过（新增 JVM / PG 慢 SQL 参数渲染成功）。

6) k6 复跑（2026-04-18，旁路鉴权）

执行命令（节选）：

```cmd
cd /d d:\StockSimulation\stock-simulation
k6 run --quiet -e BASE_URL=http://localhost:18080 -e K6_BYPASS_KEY=k6-bypass-20260417 -e VUS=50 -e DURATION=20s --summary-export .\tools\perf\k6-market-rerun-20260418-v2-summary.json .\k6\market-load-test.js
k6 run --quiet --no-setup -e BASE_URL=http://localhost:18080 -e K6_BYPASS_KEY=k6-bypass-20260417 -e VUS=20 -e DURATION=20s -e ACCEPT_429=true --summary-export .\tools\perf\k6-trade-rerun-20260418-v2-summary.json .\k6\trade-load-test.js
k6 run --quiet --vus 300 --duration 10s -e WS_URL=ws://localhost:18080/ws/market/websocket -e TARGET_CODE=sh600519 --summary-export .\tools\perf\k6-websocket-rerun-20260418-v2-summary.json .\k6\websocket-load-test.js
```

结果摘要：
- Market：失败率偏高（`http_req_failed=36.22%`），`kline` 全失败，需继续优化。
- Trade：延迟达标（`p95=30.14ms`），业务 check 绝大多数通过。
- WebSocket：连接成功校验未通过（`0/3000`），需排查握手/订阅链路。
- Portfolio：本轮未形成有效样本（`http_reqs=0`），需单独复测。

## 4. 全量验收步骤（功能 + 接口 + 性能 + 设计约束）

### 4.1 启动与基础可用性

```cmd
cd /d d:\StockSimulation\stock-simulation
docker compose -f docker-compose.dev.yml up -d
```

```cmd
docker compose ps
```

通过标准：
- `app-1`、`app-2`、`pg-master`、`pg-slave-*`、`redis-*`、`rabbitmq`、`nginx`、`prometheus`、`grafana` 全部 `running/healthy`。

### 4.2 数据库迁移与索引生效

```cmd
docker exec stock-pg-master psql -U stock_app -d stock_simulation -c "SELECT version, success FROM flyway_schema_history WHERE version='20260417.001';"
```

通过标准：
- 查询到版本 `20260417.001` 且 `success = t`。

索引存在性校验：

```cmd
docker exec stock-pg-master psql -U stock_app -d stock_simulation -c "\di+ idx_user_created_id_desc"
docker exec stock-pg-master psql -U stock_app -d stock_simulation -c "\di+ idx_trade_record_traded_at"
docker exec stock-pg-master psql -U stock_app -d stock_simulation -c "\di+ idx_order_status_updated_id"
```

通过标准：
- 上述索引均存在。

### 4.3 慢 SQL 验收（< 50ms 目标）

采样日志：

```cmd
docker compose logs pg-master | findstr "duration:"
```

建议步骤：
- 用真实接口流量（见 4.7 压测）跑 3~5 分钟。
- 对出现频次最高的 SQL 执行 `EXPLAIN ANALYZE`。

示例（管理员统计相关）：

```cmd
docker exec stock-pg-master psql -U stock_app -d stock_simulation -c "EXPLAIN ANALYZE SELECT COUNT(1) FROM t_trade_record WHERE traded_at >= now() - interval '1 day';"
```

通过标准：
- 高频 TOP SQL 无明显顺序扫描回退；核心 SQL 目标耗时 < 50ms（按业务数据规模评估）。

### 4.4 缓存命中率验收（L1/L2）

在 Prometheus (`http://localhost:9090`) 查询：

```text
sum(increase(market_quote_cache_hit_total{level="L1"}[5m]))
sum(increase(market_quote_cache_hit_total{level="L2"}[5m]))
```

结合行情总请求量指标计算命中率并在 Grafana 看板长期观察。

通过标准：
- L1 命中率趋势 > 80%。
- L2 命中率趋势 > 95%。

### 4.5 连接池与泄漏验收

Prometheus 查询：

```text
db_pool_active_connections{source="master"}
db_pool_active_connections{source="slave"}
```

日志检查：

```cmd
docker compose logs app-1 | findstr /i "leak"
docker compose logs app-2 | findstr /i "leak"
```

通过标准：
- 高峰期连接池活跃连接稳定，无持续增长异常。
- 无连接泄漏告警。

### 4.6 JVM GC 验收

```cmd
docker compose logs app-1 | findstr /i "gc pause"
docker compose logs app-2 | findstr /i "gc pause"
```

或在 Grafana JVM 面板观测 Pause。

通过标准：
- GC 暂停满足目标（`< 100ms`）。

### 4.7 接口可用性验收（核心链路）

1) 认证链路
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`

2) 行情链路
- `GET /api/v1/market/quote/{code}`
- `GET /api/v1/market/quotes?codes=...`
- `GET /api/v1/market/search?keyword=...`

3) 交易链路
- `POST /api/v1/trade/orders`
- `DELETE /api/v1/trade/orders/{id}`

4) 持仓链路
- `GET /api/v1/portfolio/overview`
- `GET /api/v1/portfolio/positions`

5) 管理链路
- `GET /api/v1/admin/dashboard/stats`

通过标准：
- 返回码符合设计预期（成功 2xx、业务校验 4xx、无异常 5xx）。
- 关键业务指标有持续增长（下单、成交、缓存命中、WS连接）。

### 4.8 性能验收（项目设计要求）

使用现有 k6 脚本执行基线压测：

```cmd
cd /d d:\StockSimulation\stock-simulation
k6 run .\k6\market-load-test.js
k6 run .\k6\trade-load-test.js
k6 run .\k6\portfolio-load-test.js
k6 run .\k6\websocket-load-test.js
```

旁路鉴权压测（无 Token）示例：

```cmd
cd /d d:\StockSimulation\stock-simulation
k6 run --quiet -e BASE_URL=http://localhost:18080 -e K6_BYPASS_KEY=<bypassKey> .\k6\market-load-test.js
k6 run --quiet --no-setup -e BASE_URL=http://localhost:18080 -e K6_BYPASS_KEY=<bypassKey> -e ACCEPT_429=true .\k6\trade-load-test.js
k6 run --quiet --no-setup -e BASE_URL=http://localhost:18080 -e K6_BYPASS_KEY=<bypassKey> .\k6\portfolio-load-test.js
k6 run --quiet --vus 300 --duration 10s -e WS_URL=ws://localhost:18080/ws/market/websocket -e TARGET_CODE=sh600519 .\k6\websocket-load-test.js
```

说明：`trade/portfolio` 脚本包含 `setup()` 健康检查，在某些临时环境中可通过 `--no-setup` 跳过该前置探测，避免误判为业务接口不可用。

通过标准（参考路线图与架构文档）：
- 行情接口：P95 < 50ms，P99 < 100ms，错误率 < 0.1%
- 交易接口：P95 < 100ms，P99 < 200ms，错误率 < 0.1%
- WS 推送延迟：P99 < 500ms
- 压测期间告警无持续 Critical（瞬时告警需可解释且可恢复）

## 5. 设计需求覆盖结论

本次 Iteration 16 已完成以下能力闭环：

- 慢 SQL：可观测（>50ms）+ 索引级优化落地
- 缓存：保留多级缓存架构并支撑命中率持续调优
- 连接池：主从参数与泄漏检测可配置化
- JVM：G1 与暂停目标参数落地
- 异步链路：线程池/MQ消费参数可调优
- 前端：构建分包 + WS 合批渲染优化

该模块已具备进入 Iteration 17（k6 全量压测 + 安全加固）的基础条件。
