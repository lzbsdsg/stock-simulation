# Iteration 17 性能优化与压测报告

## 1. 报告范围

本报告覆盖本地完整拓扑下的三类结果：

- 后端性能与并发优化后的运行设计
- 全链路混合流量、单接口统一 QPS、WebSocket 推送压测
- 当前最优参数组合、脚本参数含义与复现命令

时间：2026-04-24

## 2. 本轮优化摘要

### 2.1 请求链路与线程模型

本轮对热点链路做了隔离与削峰，避免默认公共线程池和业务请求线程互相争抢：

- `src/main/java/com/lzbsdsg/stocksimulation/config/AsyncConfig.java`
  - 新增 `marketProviderExecutor`
  - 新增 `marketIngestExecutor`
- `src/main/java/com/lzbsdsg/stocksimulation/market/domain/service/MarketDataFacade.java`
  - provider fan-out 改为使用独立 `marketProviderExecutor`
  - K 线缓存填充等待窗口缩短
  - 未拿到缓存填充锁时改为 DB-only stale fallback，减少请求线程阻塞
- `src/main/java/com/lzbsdsg/stocksimulation/market/infrastructure/ingest/MarketIngestService.java`
  - ingest fan-out 改为使用独立 `marketIngestExecutor`

### 2.2 交易与查询热点

- `src/main/java/com/lzbsdsg/stocksimulation/trade/application/TradeApplicationService.java`
  - `scope=today` 走活动订单快速路径，避免不必要的 archive 联查
- `src/main/java/com/lzbsdsg/stocksimulation/trade/infrastructure/persistence/OrderRepositoryImpl.java`
  - 新增 active today 分页查询与 count 路径
- `src/main/resources/db/migration/V20260424_001__add_hot_paging_indexes.sql`
  - 增加订单 today 分页与资金流水分页热点索引

### 2.3 热路径日志降噪

以下高频路径成功日志已由 info 收敛到 debug，降低同步日志开销：

- `src/main/java/com/lzbsdsg/stocksimulation/trade/infrastructure/mq/OrderMessageProducer.java`
- `src/main/java/com/lzbsdsg/stocksimulation/trade/infrastructure/mq/MatchConsumer.java`
- `src/main/java/com/lzbsdsg/stocksimulation/trade/application/TradeApplicationService.java`

### 2.4 压测脚本统一口径

- `k6/perf-fullchain-and-endpoints.js`
  - 统一输出 total QPS、full-chain success/sec、业务 success/sec、场景级延迟与失败率
- `k6/perf-endpoints-common-qps.js`
  - 统一输出每个 endpoint 的 configured_rate、QPS、success/sec、P95/P99、失败率、预分配并发
- `k6/websocket-load-test.js`
  - 增加连接成功率与推送时延采样口径

## 3. 压测环境

### 3.1 拓扑

本轮使用本地完整拓扑，而不是单进程直连：

- PostgreSQL：1 主 2 从
- Redis：3 主 3 从集群
- RabbitMQ
- Spring Boot 双实例：`app-1`、`app-2`
- Nginx 统一入口与 WebSocket 代理

关键文件：

- `docker-compose.dev.yml`
- `nginx/nginx.conf`
- `src/main/resources/application.yml`
- `src/main/resources/application-dev.yml`

### 3.2 运行态确认

正式压测前已确认：

- `app-1` 健康：`/actuator/health = UP`
- `app-2` 健康：`/actuator/health = UP`
- `nginx` 入口健康：`/actuator/health = UP`
- 受保护 API 通过 k6 旁路头可正常访问

## 4. 当前最优参数组合

### 4.1 应用与基础设施

当前已落入代码/配置的主要参数：

- Tomcat
  - `SERVER_TOMCAT_THREADS_MAX=600`
  - `SERVER_TOMCAT_THREADS_MIN_SPARE=80`
  - `SERVER_TOMCAT_ACCEPT_COUNT=40000`
  - `SERVER_TOMCAT_MAX_CONNECTIONS=100000`
- 连接池
  - `DB_MASTER_MAX_POOL_SIZE=50`
  - `DB_SLAVE_MAX_POOL_SIZE=80`
- Redis
  - `REDIS_POOL_MAX_ACTIVE=128`
  - `REDIS_POOL_MAX_IDLE=64`
  - `REDIS_POOL_MIN_IDLE=16`
  - `REDIS_CLUSTER_REFRESH_PERIOD=15s`
- Market executor
  - `APP_ASYNC_MARKET_PROVIDER_CORE_POOL_SIZE=24`
  - `APP_ASYNC_MARKET_PROVIDER_MAX_POOL_SIZE=96`
  - `APP_ASYNC_MARKET_PROVIDER_QUEUE_CAPACITY=6000`
  - `APP_ASYNC_MARKET_INGEST_CORE_POOL_SIZE=16`
  - `APP_ASYNC_MARKET_INGEST_MAX_POOL_SIZE=48`
  - `APP_ASYNC_MARKET_INGEST_QUEUE_CAPACITY=3000`
- RabbitMQ consumer
  - `APP_RABBIT_MATCH_PREFETCH=30`
  - `APP_RABBIT_MATCH_CONCURRENT_CONSUMERS=12`
  - `APP_RABBIT_MATCH_MAX_CONCURRENT_CONSUMERS=24`
- WebSocket
  - `MARKET_WS_ATTACH_PUSH_TIMESTAMP=true`
  - `MARKET_WS_PUSH_INTERVAL_MS=200`
  - `MARKET_WS_DEGRADED_PUSH_INTERVAL_MS=1000`
  - `MARKET_WS_DRAIN_BATCH_SIZE=64`
  - `MARKET_WS_DEGRADED_DRAIN_BATCH_SIZE=256`
- Nginx
  - `limit_req_zone rate=2000r/s`
  - `limit_conn ws_conn 50000`

### 4.2 策略解释

当前最优策略不是单点暴力扩线程，而是：

1. 行情 provider 回源与 ingest 拉取走独立线程池
2. 请求线程尽量不等待 K 线首次回源完成
3. `today` 高频订单查询走活动表快速路径
4. 高热 MQ/撮合/通知链路减少 info 级同步日志
5. 压测口径统一为 constant-arrival-rate，而不是 VU + sleep

## 5. 压测脚本与参数说明

### 5.1 全链路混合脚本

文件：`k6/perf-fullchain-and-endpoints.js`

主要参数：

- `BASE_URL`：压测入口，本次使用 `http://nginx`
- `K6_BYPASS_KEY`：旁路鉴权密钥
- `DURATION`：场景持续时间
- `FULL_CHAIN_RPS`：混合流量的恒定到达率
- `FULL_CHAIN_PRE_ALLOCATED_VUS`：预分配并发工作线程
- `FULL_CHAIN_MAX_VUS`：最大并发工作线程
- `MARKET_RATIO / PORTFOLIO_RATIO / TRADE_RATIO`：业务比例
- `QUOTE_RPS / PORTFOLIO_RPS / TRADE_LIST_RPS`：并行单接口场景的恒定到达率

输出指标：

- `total_qps`
- `business_success_total`
- `business_success_per_second`
- `full_chain_success_total`
- `full_chain_success_per_second`
- 每个 scenario 的 `qps / success_per_second / p95 / p99 / failed_rate`

### 5.2 单接口统一 QPS 脚本

文件：`k6/perf-endpoints-common-qps.js`

主要参数：

- `BASE_URL`
- `K6_BYPASS_KEY`
- `DURATION`
- `RPS_*`：每个 endpoint 的恒定到达率
- `K6_USER_ID_BASE / K6_USER_ID_SPAN`：旁路鉴权下的用户范围轮转
- `RATE_LIMIT_IDENTITY_PREFIX`：限流身份头前缀，避免被全局限流合并

输出指标：

- `total_qps`
- `endpoint_success_total`
- `endpoint_success_per_second`
- 每个 endpoint 的：
  - `configured_rate`
  - `qps`
  - `success_per_second`
  - `preAllocatedVUs`
  - `maxVUs`
  - `p95 / p99`
  - `failed_rate`

### 5.3 WebSocket 脚本

文件：`k6/websocket-load-test.js`

主要参数：

- `BASE_URL`
- `K6_BYPASS_KEY`
- `DURATION`
- `VUS`：并发连接数
- `WS_URL`：本次使用 `ws://nginx/ws/market-native`
- `TARGET_CODE`：订阅的股票代码
- `WS_SESSION_MS`：单连接会话时长
- `WS_HEARTBEAT_MS`：STOMP 心跳周期

输出指标：

- `ws_connect_success_rate`
- `ws_stomp_connected_total`
- `ws_received_total`
- `ws_push_latency_ms`

## 6. 本轮复现命令

在 Git Bash 下为避免 Docker 路径转换问题，本轮统一加：

- `MSYS_NO_PATHCONV=1`
- `MSYS2_ARG_CONV_EXCL='*'`

### 6.1 Mixed / full-chain

```bash
MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*' docker run --rm \
  --network stock-simulation_stock-network \
  -v /d/StockSimulation/stock-simulation:/work \
  -w /work \
  grafana/k6:1.7.1 run /work/k6/perf-fullchain-and-endpoints.js \
  -e BASE_URL=http://nginx \
  -e K6_BYPASS_KEY=k6-bypass-20260420 \
  -e DURATION=60s \
  -e FULL_CHAIN_RPS=180 \
  -e QUOTE_RPS=90 \
  -e PORTFOLIO_RPS=45 \
  -e TRADE_LIST_RPS=45
```

### 6.2 Endpoints common QPS

```bash
MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*' docker run --rm \
  --network stock-simulation_stock-network \
  -v /d/StockSimulation/stock-simulation:/work \
  -w /work \
  grafana/k6:1.7.1 run /work/k6/perf-endpoints-common-qps.js \
  -e BASE_URL=http://nginx \
  -e K6_BYPASS_KEY=k6-bypass-20260420 \
  -e DURATION=60s \
  -e RPS_MARKET_QUOTE=120 \
  -e RPS_MARKET_QUOTES_BATCH=80 \
  -e RPS_MARKET_KLINE=40 \
  -e RPS_MARKET_SEARCH=60 \
  -e RPS_MARKET_LISTED=70 \
  -e RPS_MARKET_INDEXES=60 \
  -e RPS_MARKET_REALTIME_METRICS=30 \
  -e RPS_PORTFOLIO_OVERVIEW=40 \
  -e RPS_PORTFOLIO_POSITIONS=90 \
  -e RPS_PORTFOLIO_FUND_FLOWS=70 \
  -e RPS_PORTFOLIO_EQUITY_CURVE=60 \
  -e RPS_TRADE_ORDERS=40 \
  -e RPS_TRADE_TRADES=80 \
  -e RPS_WATCHLIST_GET=80 \
  -e RPS_NOTIFICATIONS_GET=70 \
  -e RPS_NOTIFICATIONS_UNREAD_COUNT=60 \
  -e RPS_USER_ME=20
```

### 6.3 WebSocket

```bash
MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*' docker run --rm \
  --network stock-simulation_stock-network \
  -v /d/StockSimulation/stock-simulation:/work \
  -w /work \
  grafana/k6:1.7.1 run /work/k6/websocket-load-test.js \
  -e BASE_URL=http://nginx \
  -e K6_BYPASS_KEY=k6-bypass-20260420 \
  -e DURATION=60s \
  -e VUS=1000 \
  -e TARGET_CODE=sh600519 \
  -e WS_URL=ws://nginx/ws/market-native \
  -e WS_SESSION_MS=55000
```

## 7. 压测结果

### 7.1 全链路混合压测

文件：`tools/perf/iteration17-final-mixed.txt`

结果摘要：

- `total_requests = 32404`
- `total_qps = 539.13`
- `total_failed_rate = 0.00%`
- `total_p95_ms = 20.73`
- `total_p99_ms = 67.02`
- `business_success_total = 32404`
- `business_success_per_second = 539.13`
- `full_chain_success_total = 10801`
- `full_chain_failure_total = 0`
- `full_chain_success_per_second = 179.71`
- `full_chain_p95_ms = 35.00`
- `full_chain_p99_ms = 132.00`

场景级结果：

| scenario | configured_rate | qps | success_per_second | p95(ms) | p99(ms) | failed_rate |
|---|---:|---:|---:|---:|---:|---:|
| endpoint_market_quote | 90 | 89.86 | 89.86 | 3.86 | 43.36 | 0.00% |
| endpoint_portfolio_overview | 45 | 44.92 | 44.92 | 36.53 | 173.69 | 0.00% |
| endpoint_trade_list | 45 | 44.94 | 44.94 | 9.88 | 79.31 | 0.00% |
| full_chain_mix | 180 | 359.41 | 359.41 | 19.21 | 62.23 | 0.00% |

说明：`full_chain_mix` 每次迭代会串行打 2 个业务请求，因此其请求级 QPS 高于 `FULL_CHAIN_RPS`，而真正的全链路成功数以 `full_chain_success_total / full_chain_success_per_second` 为准。

### 7.2 单接口统一 QPS 压测

文件：`tools/perf/iteration17-final-endpoints.txt`

总览：

- `total_requests = 64211`
- `total_qps = 1068.69`
- `total_failed_rate = 0.00%`
- `total_p95_ms = 10.42`
- `total_p99_ms = 65.56`
- `endpoint_success_total = 64211`
- `endpoint_success_per_second = 1068.69`

关键接口结果：

| endpoint | configured_rate | qps | success_per_second | p95(ms) | p99(ms) | failed_rate |
|---|---:|---:|---:|---:|---:|---:|
| ep_market_quote | 120 | 119.83 | 119.83 | 3.72 | 51.46 | 0.00% |
| ep_market_kline | 40 | 39.94 | 39.94 | 4.40 | 51.09 | 0.00% |
| ep_market_listed | 70 | 69.90 | 69.90 | 6.22 | 53.36 | 0.00% |
| ep_market_search | 60 | 59.93 | 59.93 | 5.77 | 55.19 | 0.00% |
| ep_portfolio_overview | 40 | 39.96 | 39.96 | 27.33 | 148.35 | 0.00% |
| ep_portfolio_positions | 90 | 89.89 | 89.89 | 28.01 | 173.68 | 0.00% |
| ep_portfolio_fund_flows | 70 | 69.92 | 69.92 | 8.85 | 82.55 | 0.00% |
| ep_trade_orders | 40 | 39.94 | 39.94 | 8.18 | 54.02 | 0.00% |
| ep_trade_trades | 80 | 79.91 | 79.91 | 8.29 | 66.73 | 0.00% |
| ep_watchlist_get | 80 | 79.91 | 79.91 | 4.53 | 57.34 | 0.00% |
| ep_notifications_get | 70 | 69.92 | 69.92 | 7.58 | 71.31 | 0.00% |
| ep_user_me | 20 | 19.99 | 19.99 | 6.44 | 50.18 | 0.00% |

结论：在当前本地完整拓扑下，本轮所有纳入统一 QPS 脚本的接口均实现 0 技术失败率，且实际 QPS 与 configured rate 基本对齐。

### 7.3 WebSocket 压测

文件：

- 正式跑：`tools/perf/iteration17-final-websocket.txt`
- 调试复测：`tools/perf/iteration17-websocket-debug.txt`

正式跑（1000 连接，60s）结果：

- `ws_connect_success_rate = 100.00%`
- `ws_stomp_connected_total = 2000`
- `ws_msgs_received = 2000`
- `ws_latency_samples_total = 0`
- `ws_push_latency_ms = 0`
- 失败原因：阈值 `ws_latency_samples_total count>0` 未满足

调试复测（3 连接，20s，DEBUG_WS_PAYLOAD=true）结果：

- `ws_connect_success_rate = 100.00%`
- `ws_latency_samples_total = 24`
- `ws_push_latency_ms p(99) = 6ms`
- `ws_stomp_connected_total = 6`
- `ws_received_total = 24`

结论：

- WebSocket 握手、STOMP CONNECT/SUBSCRIBE、消息接收链路是通的。
- 在高连接正式场景下，时延样本没有被脚本稳定提取，导致正式 WebSocket 时延报告未闭环；因此本轮保留“连接成功率已验证、推送时延仅在小规模调试复测下验证”的结论，不把 1000 连接时延结果当成最终最佳口径。

## 8. 当前结论

当前已确认：

- 本地双实例 + nginx + PG 主从 + Redis Cluster + RabbitMQ 的完整环境已恢复稳定
- k6 Docker 运行路径问题已修复
- k6 mixed / endpoint summary 口径已修复，输出可正确统计总 QPS 与 success/sec
- EastMoney provider 仍存在上游请求失败告警，但在当前缓存/降级路径下系统整体仍可稳定对外服务

综合本轮结果，当前最优可复现基线为：

- 全链路混合：`539.13 req/s` 请求级吞吐，`179.71 success/s` 全链路业务成功数
- 单接口总吞吐：`1068.69 req/s`
- 统一纳入 endpoint 压测的接口技术失败率：`0.00%`
- WebSocket：1000 连接握手成功率 `100%`，但正式高连接时延样本统计仍需下一轮继续收口

## 9. 证据文件

本轮新增/使用的关键产物：

- `tools/perf/iteration17-final-mixed.txt`
- `tools/perf/iteration17-final-endpoints.txt`
- `tools/perf/iteration17-final-websocket.txt`
- `tools/perf/iteration17-websocket-debug.txt`
- `tools/perf/debug-fullchain-summary.json`
- `tools/perf/debug-endpoints-summary.json`
- `tools/perf/debug-fullchain-stdout-fixed3.txt`
- `tools/perf/debug-endpoints-stdout-fixed2.txt`
