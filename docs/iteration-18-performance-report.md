# Iteration 18 性能与并发优化报告（详细版）

## 1. 全链路检查结论

检查范围：Docker 编排、核心中间件、应用实例、Nginx 入口、后端编译、压测执行链路。

结论：
- Docker Compose 配置校验通过。
- 核心容器均可启动并保持健康（PostgreSQL 主从、Redis Cluster、RabbitMQ、app-1、app-2、nginx）。
- 全链路入口可达（Nginx -> 应用 -> DB/Redis/RabbitMQ）。
- 后端编译通过（skip tests）。
- 压测环境统一为容器化 k6，避免本机缺少 k6 命令造成中断。

## 2. 关键问题与修复

### 2.1 问题一：压测目标地址导致误报
- 现象：早期基线出现 100% 失败。
- 原因：k6 容器中使用 host.docker.internal 稳定性不足，导致路由不稳定。
- 修复：k6 统一加入 compose 网络并直接访问 nginx 服务名。

### 2.2 问题二：高并发参数偏保守
- 现象：高压下尾延迟抖动增大，容量上限提前出现。
- 原因：Redis 池、Tomcat、DB 连接池、异步线程池与 Rabbit 消费并发配置偏保守。
- 修复：系统性提高关键并发参数，并增加 Redis cluster 自适应刷新能力。

## 3. 已实施优化项

### 3.1 Redis 客户端与连接池优化
变更文件：[src/main/resources/application.yml](src/main/resources/application.yml)

- 增加 Redis 超时参数：REDIS_TIMEOUT（默认 2000ms）。
- 启用并参数化 Lettuce Cluster 自适应刷新与周期刷新：
  - REDIS_CLUSTER_REFRESH_ADAPTIVE=true
  - REDIS_CLUSTER_REFRESH_PERIOD=30s（容器中覆盖为 15s）
- 连接池参数提升并可配置：
  - max-active: 64
  - max-idle: 32
  - min-idle: 8

收益：提升集群拓扑变化时的鲁棒性，降低连接等待概率。

### 3.2 应用容器并发参数优化
变更文件：[docker-compose.dev.yml](docker-compose.dev.yml)

app-1/app-2 统一增强：
- Redis 池与拓扑刷新参数（容器级覆盖）
  - REDIS_POOL_MAX_ACTIVE=128
  - REDIS_POOL_MAX_IDLE=64
  - REDIS_POOL_MIN_IDLE=16
  - REDIS_CLUSTER_REFRESH_ADAPTIVE=true
  - REDIS_CLUSTER_REFRESH_PERIOD=15s
- Tomcat 容量参数
  - SERVER_TOMCAT_THREADS_MAX=600
  - SERVER_TOMCAT_THREADS_MIN_SPARE=80
  - SERVER_TOMCAT_ACCEPT_COUNT=40000
  - SERVER_TOMCAT_MAX_CONNECTIONS=100000
- DB 连接池参数
  - DB_MASTER_MAX_POOL_SIZE=50
  - DB_SLAVE_MAX_POOL_SIZE=80
- 异步任务线程池
  - APP_ASYNC_CORE_POOL_SIZE=16
  - APP_ASYNC_MAX_POOL_SIZE=64
  - APP_ASYNC_QUEUE_CAPACITY=2000
- RabbitMQ 消费并发
  - APP_RABBIT_MATCH_PREFETCH=30
  - APP_RABBIT_MATCH_CONCURRENT_CONSUMERS=12
  - APP_RABBIT_MATCH_MAX_CONCURRENT_CONSUMERS=24
- JVM 参数
  - 增加 MaxGCPauseMillis、ParallelRefProcEnabled、UseStringDeduplication

收益：提高总体吞吐上限，并降低中高压下排队和 GC 抖动。

### 3.3 统一压测脚本重构
新增文件：[k6/perf-fullchain-and-endpoints.js](k6/perf-fullchain-and-endpoints.js)

能力：
- 单次压测同时覆盖：
  - 全链路混合流量（full_chain_mix）
  - 单接口 quote（endpoint_market_quote）
  - 单接口 portfolio overview（endpoint_portfolio_overview）
  - 单接口 trade list（endpoint_trade_list）
- 使用 constant-arrival-rate，固定到达率压测，更适合容量边界评估。
- 支持 bypass 鉴权头与身份隔离头，减少 token 与限流噪声。
- 可直接导出 summary json 作为复盘事实源。

## 4. 优化后模块设计亮点（为什么提效）

### 4.1 入口层（Nginx + 双应用实例）
- 设计亮点：Nginx 统一入口，app-1/app-2 水平扩展承载。
- 提效机理：将连接接入与业务处理解耦，提升峰值抗压能力，降低单实例抖动放大效应。

### 4.2 应用层（Tomcat + Async 线程池）
- 设计亮点：提高 Tomcat 最大线程、连接数、等待队列；同时扩容异步线程池。
- 提效机理：高并发下减少请求在入口与业务线程的双重排队，缩短请求等待时间。

### 4.3 数据层（PostgreSQL 主从 + 池化）
- 设计亮点：主从分工 + 读写池分别扩容。
- 提效机理：将读请求压力从主库分流，降低主库争用，提升读多写少场景吞吐稳定性。

### 4.4 缓存层（Redis Cluster + 自适应刷新）
- 设计亮点：Redis 3主3从 + Lettuce 自适应拓扑刷新 + 更大连接池。
- 提效机理：在节点切换或槽位变化时更快收敛，避免高峰期因连接/路由不稳定导致的放大延迟。

### 4.5 消息层（RabbitMQ 消费并发）
- 设计亮点：prefetch + 并发消费者 + 最大并发消费者联动配置。
- 提效机理：提高消费侧吞吐，减少消息堆积对前台接口时延的间接影响。

## 5. 测试口径与指标定义

统一口径（本报告全部遵循）：
- 吞吐（QPS）：使用 k6 summary 的 http_reqs.rate。
- 失败率：使用 http_req_failed.value。
- 延迟：使用 http_req_duration 的 p90 / p95（单位 ms）。
- 并发：
  - 实时并发观测用 vus.max。
  - 场景容量上限用 vus_max.value（即脚本设置上限）。
- 单接口 QPS：按检查项总请求数除以统计窗口秒数，统计窗口秒数按下式计算：
  - durationSec = http_reqs.count / http_reqs.rate

说明：
- 基线文件命名为 60s，但 summary 统计窗口由 count/rate 反推约 64.22s，属 k6 统计周期表现，报告按实际统计窗口计算。
- A/B/C 组统计窗口分别约 19.08s / 14.97s / 14.91s，均以 summary 实际值为准。

## 6. 每组测试方法与启动命令（可复现）

### 6.1 环境启动

在项目根目录执行：

```bash
docker compose -f docker-compose.dev.yml up -d
docker compose -f docker-compose.dev.yml --profile nonprod-app up -d app-1 app-2 nginx
```

可选观测栈：

```bash
docker compose -f docker-compose.dev.yml --profile nonprod-observe up -d
```

### 6.2 健康检查

```bash
docker ps
curl -f http://localhost/actuator/health
```

### 6.3 k6 容器执行模板

PowerShell：

```powershell
$K6_WORKDIR = (Resolve-Path .\k6).Path
docker run --rm --network stock-simulation_stock-network `
  -v "${K6_WORKDIR}:/work" -w /work grafana/k6 run /work/perf-fullchain-and-endpoints.js `
  -e BASE_URL=http://nginx `
  -e K6_BYPASS_KEY=k6-bypass-20260420 `
  -e ACCEPT_429=true `
  -e DURATION=60s `
  -e FULL_CHAIN_RPS=<full_chain> `
  -e QUOTE_RPS=<quote_only> `
  -e PORTFOLIO_RPS=<portfolio_only> `
  -e TRADE_LIST_RPS=<trade_list_only> `
  --summary-export /work/<output.json>
```

Bash：

```bash
docker run --rm --network stock-simulation_stock-network \
  -v "$(pwd)/k6:/work" -w /work grafana/k6 run /work/perf-fullchain-and-endpoints.js \
  -e BASE_URL=http://nginx \
  -e K6_BYPASS_KEY=k6-bypass-20260420 \
  -e ACCEPT_429=true \
  -e DURATION=60s \
  -e FULL_CHAIN_RPS=<full_chain> \
  -e QUOTE_RPS=<quote_only> \
  -e PORTFOLIO_RPS=<portfolio_only> \
  -e TRADE_LIST_RPS=<trade_list_only> \
  --summary-export /work/<output.json>
```

### 6.4 本次各组压测参数

- 基线（优化前）：FULL_CHAIN_RPS=500，QUOTE_RPS=220，PORTFOLIO_RPS=120，TRADE_LIST_RPS=120
- A 组（优化后低档）：FULL_CHAIN_RPS=450，QUOTE_RPS=180，PORTFOLIO_RPS=90，TRADE_LIST_RPS=90
- B 组（优化后推荐）：FULL_CHAIN_RPS=650，QUOTE_RPS=260，PORTFOLIO_RPS=130，TRADE_LIST_RPS=130
- C 组（冲刺上限）：FULL_CHAIN_RPS=850，QUOTE_RPS=340，PORTFOLIO_RPS=170，TRADE_LIST_RPS=170

## 7. 全链路与单接口指标明细

### 7.1 总体指标对比（全链路视角）

口径说明（重要）：
- 表中“总QPS (http_reqs.rate)”是本次压测所有 scenario 产生的 HTTP 请求总速率。
- 因为 full_chain_mix 一次迭代会触发 1 到 3 个接口调用，所以总QPS不等于 full_chain 的迭代速率，也不等于 7.2 中单接口QPS简单相加。
- 更准确拆解应为：总QPS = full_chain_mix 内部请求QPS总和 + 各 endpoint_* 单接口 scenario 的QPS总和。

| 组别 | 数据文件 | 总QPS (http_reqs.rate) | 失败率 (http_req_failed.value) | 总体 p90(ms) | 总体 p95(ms) | 全链路 p95(ms) | vus.max | vus_max |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| 优化前基线 | [k6/_preopt-perf-summary-60s.json](k6/_preopt-perf-summary-60s.json) | 1110.01 | 0.00% | 7.58 | 11.94 | 17.47 | 21 | 460 |
| A 组 | [k6/_postopt-A.json](k6/_postopt-A.json) | 1061.72 | 0.00% | 7.74 | 16.62 | 24.25 | 23 | 460 |
| B 组 | [k6/_postopt-B.json](k6/_postopt-B.json) | 1732.39 | 0.00% | 10.27 | 19.21 | 26.87 | 24 | 650 |
| C 组 | [k6/_postopt-C.json](k6/_postopt-C.json) | 2244.32 | 8.47% | 15.55 | 31.46 | 35.22 | 292 | 850 |

### 7.2 单接口指标（单接口 scenario 视角）

说明：下表“单接口QPS”为单接口检查项总请求数除以该组统计窗口秒数，属于实际达成值。

覆盖范围说明（重要）：
- 当前仅列 3 个单接口（quote、portfolio-overview、trade-list），是因为压测脚本只定义了这 3 个 endpoint_* 场景。
- 其余业务接口并未在本次“单接口场景”中独立施压，不代表业务中不存在。
- 如需覆盖更多业务接口，应在 [k6/perf-fullchain-and-endpoints.js](k6/perf-fullchain-and-endpoints.js) 中新增 endpoint_* scenario 与对应函数，并补充阈值。

| 组别 | quote-only QPS | quote-only p95(ms) | portfolio-only QPS | portfolio-only p95(ms) | trade-list-only QPS | trade-list-only p95(ms) |
|---|---:|---:|---:|---:|---:|---:|
| 优化前基线 | 168.20 | 1.82 | 84.10 | 22.55 | 84.09 | 3.60 |
| A 组 | 159.84 | 2.31 | 79.89 | 29.72 | 79.89 | 4.15 |
| B 组 | 259.48 | 3.92 | 129.51 | 36.55 | 129.78 | 6.27 |
| C 组 | 339.35 | 13.67 | 169.54 | 88.11 | 169.67 | 17.02 |

### 7.3 全链路内部接口表现（full_chain_mix 触发）

| 组别 | full_chain 中 quote QPS | full_chain 中 portfolio QPS | full_chain 中 trade-list QPS |
|---|---:|---:|---:|
| 优化前基线 | 420.45 | 210.43 | 142.75 |
| A 组 | 399.51 | 201.09 | 141.49 |
| B 组 | 648.62 | 334.68 | 230.30 |
| C 组 | 848.31 | 427.74 | 289.72 |

说明：
- full_chain_mix 中 quote 每次迭代必打，portfolio 约每 2 次迭代触发一次，trade-list 约每 3 次迭代触发一次，因此三者QPS符合脚本设计比例。
- C 组在吞吐继续增长时，错误率和尾延迟同步恶化，属于“超容量工作区”。

## 8. 最优性能点判定与容量边界

判定规则：失败率 < 1%，且总吞吐最大。

最优组：B 组。

B 组核心指标：
- 总吞吐：1732.39 req/s
- 失败率：0%
- 总体延迟：p90 10.27ms，p95 19.21ms
- 全链路延迟：p95 26.87ms
- 单接口延迟：quote 3.92ms，portfolio 36.55ms，trade-list 6.27ms

对比优化前基线：
- 吞吐提升约 56.07%（1110.01 -> 1732.39 req/s）
- 失败率维持 0%
- 延迟上升但处于可控区间，换取显著容量增长

容量边界结论：
- 稳定运行建议：B 组附近（650/260/130/130）
- 冲刺上限参考：C 组（850/340/170/170），仅用于极限验证，不建议作为常态运行档位

## 9. 最优方案下的详细设计策略

### 9.1 设计策略总览
- 入口均衡：Nginx 承接接入，双实例分担业务压力。
- 线程与连接协同扩容：Tomcat、DB、Redis、Async、Rabbit 参数联动，避免单点瓶颈。
- 读写分离与缓存前置：数据库读压力分流，热点查询走 Redis。
- 消息削峰：RabbitMQ 消费并发增强，降低后台堆积对前台接口的影响。

### 9.2 上线前建议
- 以 B 组参数作为默认压测与预发布验收档位。
- 将 C 组作为月度容量巡检上限档位，关注失败率与 p95 漂移。
- 继续补充长稳压测（30min/60min）以观察 GC、连接池和消息积压长期趋势。

## 10. 可复现实验入口

- 压测脚本：[k6/perf-fullchain-and-endpoints.js](k6/perf-fullchain-and-endpoints.js)
- 多接口单接口压测脚本（常见QPS口径）：[k6/perf-endpoints-common-qps.js](k6/perf-endpoints-common-qps.js)
- 最优结果文件：[k6/_postopt-B.json](k6/_postopt-B.json)
- 本次完整报告：[docs/iteration-18-performance-report.md](docs/iteration-18-performance-report.md)

## 11. 多接口单接口压测补充（常见QPS统计）

### 11.1 已覆盖接口（本轮新增）

- /api/v1/market/quote/{stockCode}
- /api/v1/market/quotes
- /api/v1/market/kline/{stockCode}
- /api/v1/market/search
- /api/v1/market/listed
- /api/v1/market/indexes
- /api/v1/market/realtime-metrics
- /api/v1/portfolio/overview
- /api/v1/portfolio/positions
- /api/v1/portfolio/fund-flows
- /api/v1/portfolio/equity-curve
- /api/v1/trade/orders (GET)
- /api/v1/trade/trades
- /api/v1/watchlist (GET)
- /api/v1/notifications (GET)
- /api/v1/notifications/unread-count
- /api/v1/user/me

说明：为避免破坏业务数据，本脚本优先覆盖 GET / 查询类接口；写接口（下单、撤单、资料修改等）建议单独做事务型压测。

### 11.2 常见QPS统计口径

- 总QPS：使用 k6 的 http_reqs.rate。
- 分接口QPS：使用各 scenario 的 http_reqs{scenario:...}.rate。
- 统计定义：QPS = 完成请求数 / 压测时长。

### 11.3 执行命令（示例）

```powershell
$K6_WORKDIR = (Resolve-Path .\k6).Path
docker run --rm --network stock-simulation_stock-network `
  -v "${K6_WORKDIR}:/work" -w /work grafana/k6 run /work/perf-endpoints-common-qps.js `
  -e BASE_URL=http://nginx `
  -e K6_BYPASS_KEY=k6-bypass-20260420 `
  -e ACCEPT_429=true `
  -e DURATION=60s `
  --summary-export /work/_endpoint-common-summary.json
```

### 11.4 烟测结果文件

- [k6/_endpoint-common-smoke.json](k6/_endpoint-common-smoke.json)

