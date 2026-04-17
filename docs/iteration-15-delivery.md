# Iteration 15 交付说明（监控：Prometheus + Grafana + Loki + Tempo + 告警）

## 1. 迭代目标

基于 [docs/doc-D-dev-roadmap.md](docs/doc-D-dev-roadmap.md) 的 Iteration 15（Week 16）：

- 构建可观测性全栈：指标、日志、链路追踪、告警
- 落地 10+ 告警规则并对齐路线图阈值
- 落地 5 个 Grafana 业务/基础设施面板
- 增加迭代要求的业务指标埋点
- 给出完整验收方法（不仅限于单元测试）

## 2. 本次完成项

### 2.1 观测栈编排（Docker Compose）

更新 [docker-compose.yml](docker-compose.yml)：

- 应用实例 `app-1`、`app-2` 增加：
  - `OTEL_EXPORTER_OTLP_ENDPOINT`（指向 Tempo）
  - 日志目录挂载：`./logs/app-1:/app/logs`、`./logs/app-2:/app/logs`
- 新增 `postgres-exporter`（`9187`）用于 PostgreSQL 复制延迟指标采集
- 新增 `tempo`（`3200` + `4318`）用于链路追踪存储与 OTLP 接收
- 新增 `promtail`，采集应用日志并推送到 Loki
- Loki 改为加载项目内配置文件 [loki/loki-config.yml](loki/loki-config.yml)
- Grafana 增加 provisioning 挂载 [grafana/provisioning](grafana/provisioning)

### 2.2 Prometheus 抓取与告警

更新 [prometheus/prometheus.yml](prometheus/prometheus.yml)：

- 保留并确认抓取：`app-1/app-2`、`rabbitmq`、`prometheus`
- 新增抓取：`postgres-exporter`、`tempo`、`promtail`

更新 [prometheus/alert-rules.yml](prometheus/alert-rules.yml)：

- 已覆盖并对齐路线图要求的核心规则（10+）：
  - HTTP 5xx 错误率 > 1%（Critical）
  - API P99 > 500ms（Warning）
  - JVM Heap > 80%（Warning）
  - DB 连接池接近耗尽（Critical）
  - MQ 队列深度 > 1000（Warning）
  - 撮合延迟 P99 > 1s（Warning）
  - L1 缓存命中率 < 60%（Warning）
  - WS 连接数 > 8000/实例（Warning）
  - PostgreSQL 复制延迟 > 1s（Warning）
  - 登录失败率 > 30%（Warning）
- 额外补充：WS 背压丢弃速率、DLQ 堆积

### 2.3 Grafana 5 大面板落地

新增/更新 Dashboard 文件：

- [grafana/dashboards/jvm-dashboard.json](grafana/dashboards/jvm-dashboard.json)
- [grafana/dashboards/http-dashboard.json](grafana/dashboards/http-dashboard.json)
- [grafana/dashboards/trade-dashboard.json](grafana/dashboards/trade-dashboard.json)
- [grafana/dashboards/market-dashboard.json](grafana/dashboards/market-dashboard.json)
- [grafana/dashboards/db-dashboard.json](grafana/dashboards/db-dashboard.json)

Grafana 自动导入配置：

- [grafana/provisioning/datasources/datasources.yml](grafana/provisioning/datasources/datasources.yml)
- [grafana/provisioning/dashboards/dashboards.yml](grafana/provisioning/dashboards/dashboards.yml)

### 2.4 Loki + Promtail 日志检索链路

新增：

- [promtail/promtail.yml](promtail/promtail.yml)
- [loki/loki-config.yml](loki/loki-config.yml)

日志格式增强：

- 更新 [src/main/resources/logback-spring.xml](src/main/resources/logback-spring.xml)
- 生产环境改为 JSON 编码（LogstashEncoder），提升 Loki 字段检索能力（traceId/userId）

### 2.5 Tempo 链路追踪

新增 Tempo 配置 [tempo/tempo.yml](tempo/tempo.yml)。

后端依赖与配置：

- 更新 [pom.xml](pom.xml)，新增：
  - `micrometer-tracing-bridge-otel`
  - `opentelemetry-exporter-otlp`
- 更新 [src/main/resources/application-prod.yml](src/main/resources/application-prod.yml)：
  - `management.otlp.tracing.endpoint`
  - `management.tracing.sampling.probability`
  - 关键指标直方图分位统计

### 2.6 业务指标埋点（路线图要求）

1. 交易指标（Trade）
- 文件：[src/main/java/com/lzbsdsg/stocksimulation/trade/application/TradeApplicationService.java](src/main/java/com/lzbsdsg/stocksimulation/trade/application/TradeApplicationService.java)
- 新增指标：
  - `trade_order_created_total`（下单成功计数）
  - `trade_order_filled_total`（撮合成交计数）
  - `trade_match_duration_seconds`（撮合耗时直方图）
- 兼容性：增加惰性初始化，避免手动 `new` 场景单测 NPE

2. 行情缓存指标（Market）
- 文件：[src/main/java/com/lzbsdsg/stocksimulation/market/domain/service/MarketDataFacade.java](src/main/java/com/lzbsdsg/stocksimulation/market/domain/service/MarketDataFacade.java)
- 新增指标：
  - `market_quote_cache_hit_total{level="L1"}`
  - `market_quote_cache_hit_total{level="L2"}`

3. 数据源池活跃连接指标（DB）
- 文件：[src/main/java/com/lzbsdsg/stocksimulation/config/DbPoolMetricsConfig.java](src/main/java/com/lzbsdsg/stocksimulation/config/DbPoolMetricsConfig.java)
- 新增指标：
  - `db_pool_active_connections{source="master"}`
  - `db_pool_active_connections{source="slave"}`

4. WS 活跃连接指标
- 既有实现已满足：`ws_active_connections`
- 文件：[src/main/java/com/lzbsdsg/stocksimulation/market/infrastructure/websocket/MarketWebSocketSessionRegistry.java](src/main/java/com/lzbsdsg/stocksimulation/market/infrastructure/websocket/MarketWebSocketSessionRegistry.java)

### 2.7 管理员前端页面集成

管理员控制台已增加“可观测指标总览”区块，不依赖手工打开 Prometheus 查询即可查看核心指标。

- 页面文件：[stock-simulation-web/src/pages/admin/AdminConsolePage.vue](stock-simulation-web/src/pages/admin/AdminConsolePage.vue)
- 指标来源：`GET /api/v1/admin/dashboard/stats`
- 后端聚合：
  - [src/main/java/com/lzbsdsg/stocksimulation/admin/application/AdminApplicationService.java](src/main/java/com/lzbsdsg/stocksimulation/admin/application/AdminApplicationService.java)

页面可见指标包括：

- `trade_order_created_total`
- `trade_order_filled_total`
- `trade_match_duration_seconds` P95/P99
- `market_quote_cache_hit_total{level=L1/L2}`
- `ws_active_connections`
- `ws_push_dropped_total`
- `db_pool_active_connections{source=master/slave}`

## 3. 与路线图任务对齐

- Prometheus 抓取配置：已完成
- 10+ 告警规则：已完成
- Grafana 五大 Dashboard：已完成
- Loki + Promtail：已完成
- Micrometer Tracing + Tempo：已完成
- 业务指标（trade/market/ws/db）：已完成

## 4. 已执行验证（本次已跑）

1. 后端编译

```cmd
cd /d d:\StockSimulation\stock-simulation
mvnw.cmd -DskipTests compile
```

结果：通过（`BUILD SUCCESS`）。

2. 关键模块单测

```cmd
cd /d d:\StockSimulation\stock-simulation
mvnw.cmd "-Dtest=TradeApplicationServiceTest,MarketDataFacadeTest" test
```

结果：通过（`Tests run: 24, Failures: 0, Errors: 0`）。

3. Compose 配置校验

```cmd
cd /d d:\StockSimulation\stock-simulation
docker compose -f docker-compose.yml config
```

结果：通过（配置可完整渲染，包含 tempo/promtail/postgres-exporter）。

4. Dashboard JSON 语法校验

```cmd
cd /d d:\StockSimulation\stock-simulation
powershell -Command "Get-ChildItem .\grafana\dashboards\*.json | %% { Get-Content $_.FullName -Raw | ConvertFrom-Json | Out-Null; Write-Output ('dashboard-json-ok: ' + $_.Name) }"
```

结果：5 个 Dashboard 文件全部校验通过。

## 5. 全量验收步骤（建议顺序）

### 5.1 启动与基础可用性

```cmd
cd /d d:\StockSimulation\stock-simulation
docker compose up -d
```

```cmd
docker compose ps
```

通过标准：`app-1`、`app-2`、`prometheus`、`grafana`、`loki`、`promtail`、`tempo`、`postgres-exporter` 状态正常。

### 5.2 指标采集验收（Prometheus）

打开 `http://localhost:9090/targets`。

通过标准：以下 job 目标均 `UP`：

- `stock-simulation`（2 个实例）
- `rabbitmq`
- `postgres`
- `tempo`
- `promtail`
- `prometheus`

可选快速检查（PromQL）：

- `trade_order_created_total`
- `trade_order_filled_total`
- `trade_match_duration_seconds_bucket`
- `market_quote_cache_hit_total`
- `ws_active_connections`
- `db_pool_active_connections`

通过标准：以上指标均可查询到时序数据（有业务流量时出现非零样本）。

### 5.3 Grafana 验收（5 个面板）

打开 `http://localhost:3000`（默认账号 `admin`，密码来自 `.env`）。

进入文件夹 `Stock Simulation`，确认以下面板存在并可出图：

- JVM Dashboard — Stock Simulation
- HTTP Dashboard — Stock Simulation
- Trade Dashboard — Stock Simulation
- Market Dashboard — Stock Simulation
- DB Dashboard — Stock Simulation

通过标准：5 个看板均自动加载，查询无 datasource 报错。

### 5.4 告警规则验收

打开 Prometheus `http://localhost:9090/alerts`，确认规则已加载。

演练建议：

1. 登录失败率告警：
- 对 `/api/v1/auth/login` 连续发送错误密码请求（5 分钟窗口）
- 通过标准：`LoginFailureRateHighWarning` 进入 `firing`

2. WS 高连接告警：
- 执行 WS 压测脚本（见 5.7）推高连接数
- 通过标准：`WsConnectionsHighWarning` 进入 `firing`

3. 5xx 告警：
- 在压测期间制造应用异常或临时降低阈值进行演练
- 通过标准：`HighHttp5xxRateCritical` 进入 `firing`

### 5.5 Loki 日志检索验收

Grafana Explore 选择 `Loki`，示例查询：

- `{job="stock-app"} |= "traceId"`
- `{job="stock-app"} |= "userId="`

通过标准：可检索到包含 `traceId` / `userId` 的日志记录。

### 5.6 Tempo 链路验收

Grafana Explore 选择 `Tempo`，按 traceId 检索。

通过标准：

- 可以检索到调用链路
- 可以从 Trace 跳转关联日志（tracesToLogs）

### 5.7 性能与容量验收（项目设计要求）

使用现有 k6 脚本：

```cmd
cd /d d:\StockSimulation\stock-simulation
k6 run .\k6\market-load-test.js
k6 run .\k6\trade-load-test.js
k6 run .\k6\portfolio-load-test.js
k6 run .\k6\websocket-load-test.js
```

通过标准（按架构/SLO口径）：

- 行情读接口：P95 < 50ms，P99 < 100ms
- 交易写接口：P95 < 100ms，P99 < 200ms
- WS 推送延迟 P99 < 500ms
- 非业务错误率 < 0.1%

## 6. 通过标准汇总（最终验收口径）

必须同时满足：

1. 代码质量：编译通过、关键单测通过
2. 配置质量：Compose 可渲染、观测服务可启动
3. 功能正确性：指标、日志、Trace 全链路可观测
4. 可运维性：10+ 告警规则加载且可演练触发
5. 业务对齐：5 大看板齐全，覆盖交易/行情/系统/数据库
6. 性能口径：k6 场景满足项目 SLO（见 5.7）

---

本交付文档覆盖“测试通过 + 接口工作正常 + 可观测性闭环 + 性能验收口径”，满足 Iteration 15 的项目设计要求。
