# Stock Simulation

一个面向个人投资者学习与练习的高并发模拟炒股系统。项目围绕“接近真实 A 股交易规则 + 可横向扩展的后端架构 + 可观测与可压测”来设计，覆盖认证、行情、仿真交易、持仓资产、自选股、通知、管理员看板等完整业务闭环。

项目不是简单的 CRUD 练习，而是按高并发系统思路实现：

- Spring Boot 3 + Vue 3 前后端分离
- PostgreSQL 主从读写分离
- Redis Cluster 3 主 3 从
- RabbitMQ 异步撮合与通知解耦
- Caffeine + Redis 多级缓存
- WebSocket/STOMP 实时行情推送
- Prometheus + Grafana + Loki + Tempo 可观测体系
- k6 压测与容量分档验证

## 项目亮点

- 仿真交易规则贴近 A 股真实约束，支持 T+1、涨跌停、最小交易单位、手续费、资金冻结/解冻、订单撤销、成交记录与收益统计。
- 行情链路不是静态轮询，而是“可见股票集合驱动”的实时调度：前端上报可见股票集合，后端按活跃股票优先抓取、缓存、广播和推送。
- 读多写少场景做了系统级优化：L1 Caffeine + L2 Redis 多级缓存、主从读写分离、热点查询索引、异步线程池拆分、MQ 解耦、WebSocket 背压控制。
- 工程化较完整：Flyway 迁移、OpenAPI、统一异常与结果封装、限流、TraceId、Prometheus 指标、链路追踪、结构化日志、接口/API/集成/并发测试。

## 核心业务能力

- 认证与安全
  - 邮箱 OTP 注册/登录、密码登录、JWT 刷新与登出、密码重置
  - Swagger 按配置开关暴露
  - Redis + Lua 限流、K6 旁路鉴权、TraceId 透传

- 行情中心
  - 单股行情、批量行情、搜索、上市股票列表、指数、历史 K 线
  - 多 Provider 接入与容错降级
  - 活跃股票集合驱动抓取、Redis Pub/Sub 扇出、实时指标接口

- 仿真交易
  - 下单、撤单、订单列表、成交列表
  - 仿真撮合引擎、费用计算、资金冻结/解冻、MQ 异步撮合
  - 幂等、事务边界、乐观锁与并发控制

- 资产与用户中心
  - 账户总览、持仓、资金流水、收益曲线
  - 用户资料修改、修改密码
  - 自选股管理、通知中心

- 管理后台
  - 用户管理、状态调整、排行榜、统计看板

## 架构设计

### 后端分层

项目采用 DDD-lite 四层结构：

- Controller：REST/WS 接口适配、参数校验、统一返回
- Application：用例编排、事务边界、服务聚合
- Domain：交易规则、撮合、资产计算、行情策略等核心业务逻辑
- Infrastructure：MyBatis-Plus、Redis、RabbitMQ、行情 Provider、缓存、配置实现

### 基础设施拓扑

- Nginx：统一入口、反向代理、负载均衡、WebSocket 代理
- Spring Boot 双实例：业务服务水平扩展
- PostgreSQL：1 主 2 从，写走主库、读走从库
- Redis Cluster：3 主 3 从，承载缓存、限流、分布式协调、Pub/Sub
- RabbitMQ：撮合、通知、邮件等异步链路
- Prometheus + Grafana + Loki + Tempo：指标、日志、链路观测

### 性能关键设计

- 多级缓存：行情快照、股票信息、配置类数据走 `Caffeine(L1) + Redis(L2)`
- 读写分离：`@ReadOnly` + `AbstractRoutingDataSource` 实现主从路由
- 限流：Redis + Lua 实现统一限流切面，返回 `X-RateLimit-*` 头
- 行情抓取：活跃股票优先 + round-robin 补齐，降低无效抓取
- 异步线程池拆分：公共线程池、行情 Provider 线程池、行情 ingest 线程池分离
- WebSocket 背压：连接上限、队列深度控制、批量 drain、退化推送模式
- 交易一致性：账户冻结/解冻、乐观锁、行级锁、幂等与短事务设计

## 技术栈

### 后端

- Java 17
- Spring Boot 3.2
- Spring Security
- Spring WebSocket / STOMP
- MyBatis-Plus
- Flyway
- PostgreSQL
- Redis / Lettuce
- RabbitMQ
- Caffeine
- Micrometer / Prometheus
- OpenTelemetry / Tempo

### 前端

- Vue 3
- TypeScript
- Vite
- Pinia
- Vue Router
- Element Plus
- ECharts
- Axios
- SockJS + STOMP

### 工程与测试

- Maven / pnpm
- Spotless / ESLint / Prettier
- JUnit 5 / Spring Boot Test / Spring Security Test / Vitest
- k6 压测
- Docker Compose

## 目录说明

```text
src/main/java/com/lzbsdsg/stocksimulation
├─ auth            认证与 JWT
├─ user            用户资料与资金账户
├─ market          行情查询、抓取、缓存、WebSocket 推送
├─ trade           下单、撤单、撮合、成交
├─ portfolio       持仓、总览、资金流水、收益曲线
├─ watchlist       自选股
├─ notification    通知中心
├─ admin           管理员接口
├─ common          通用结果、异常、缓存、切面、工具
└─ config          安全、数据源、Redis、MQ、Swagger、WebSocket 等配置
```

## 接口概览

主要 REST API 前缀如下：

- `/api/v1/auth`：注册、登录、OTP、刷新、登出、忘记密码
- `/api/v1/user`：用户资料、密码修改
- `/api/v1/market`：行情、搜索、K 线、指数、可见股票上报、实时指标
- `/api/v1/trade`：下单、撤单、订单列表、成交列表
- `/api/v1/portfolio`：总览、持仓、资金流水、收益曲线
- `/api/v1/watchlist`：自选股管理
- `/api/v1/notifications`：通知列表、已读、未读数
- `/api/v1/admin`：用户管理、统计、排行榜

WebSocket 入口：

- `/ws/market-native`
- 主题前缀：`/topic/market/quote/{stockCode}`

## 性能表现

最新一轮压测报告见 [docs/performance-report-2026-04-26.md](docs/performance-report-2026-04-26.md)。

### 1. HTTP 推荐稳态档

当前项目最适合作为 README 主展示结果的，是 `60s` 稳态混合压测的 **推荐档 F**：

- `FULL_CHAIN_RPS=650`
- `QUOTE_RPS=260`
- `PORTFOLIO_RPS=130`
- `TRADE_LIST_RPS=130`

结果：

- 总吞吐：`1755.48 req/s`
- 总延迟：`p95=11.25ms`，`p99=30.70ms`
- 全链路业务：`39001` 成功，`0` 失败
- 全链路耗时：`p95=31ms`，`p99=47ms`
- 行情独立场景：`250.79 qps`，`p95=2.61ms`
- 资产总览独立场景：`125.40 qps`，`p95=26.42ms`
- 订单列表独立场景：`125.40 qps`，`p95=5.89ms`

为什么选它：

- 比 `450/180/90/90`、`550/220/110/110`、`600/240/120/120` 吞吐更高
- `business_failure_total=0`、`full_chain_failure_total=0`
- 再上探到 `800/320/160/160` 后，已经出现 `8642` 次业务失败，不再属于稳态容量

### 2. 查询接口族

顺序压测 `k6/perf-endpoints-common-qps.js` 的最新结果：

- 总吞吐：`1301.61 req/s`
- 总延迟：`p95=5.57ms`，`p99=19.66ms`
- `endpoint_failure_total=0`

关键接口：

- `market_quote`：`212.09 qps`，`p95=2.11ms`，`p99=3.75ms`
- `portfolio_overview`：`115.69 qps`，`p95=21.56ms`，`p99=33.53ms`
- `portfolio_positions`：`86.77 qps`，`p95=21.41ms`，`p99=33.83ms`
- `trade_orders`：`96.40 qps`，`p95=4.92ms`，`p99=7.02ms`

结论：

- 行情、订单、通知、自选股等查询链路整体健康。
- `portfolio_overview` 和 `portfolio_positions` 已通过优化后复测：`2s` 短 TTL 资产查询缓存、分页 count 复用和持仓分页排序索引把两条资产聚合查询从约 `500ms` P95 降到约 `21ms` P95。
- 后续仍需要继续跟踪资产查询 P99，并评估账户/持仓快照或结果聚合字段缓存，进一步降低复杂查询尾延迟。

### 3. WebSocket

顺序压测 `k6/websocket-load-test.js` 的当前稳定结论：

- `500` 并发连接
- `500` 次 STOMP 建连成功
- `3500` 条有效推送时延样本
- 握手 `p95=291.73ms`
- 推送时延 `p95=41ms`

说明：

- 本轮压测流量来自单一压测源，Nginx `ip_hash` 会把连接集中打到同一实例。
- 因此这个结果更接近“单实例可稳定承载 500 同源连接”，而不是双实例平均分摊后的集群上限。

## 为什么这组结论可信

- 压测入口统一走 `Nginx -> 双实例应用 -> PostgreSQL/Redis/RabbitMQ`，不是单机假环境。
- 报告同时给出稳态档与上限探测，不用极限数字误导系统容量。
- HTTP 结果以 `business_failure_total` / `full_chain_failure_total` 为准，避免 `expectedStatuses` 对 `http_req_failed` 的统计口径干扰。
- 每个正式场景都配套抓取了 JVM / GC / 线程 / 连接池指标，而不只看 `QPS` 和延迟。
- WebSocket 结果单独顺序跑，避免和查询场景互相争抢资源。

## 测试与质量保障

项目测试覆盖了：

- Application Service 测试
- Controller API 测试
- Redis / 数据源 / 缓存 / 网关集成测试
- 行情缓存与 Provider 降级测试
- WebSocket 握手与背压测试
- 撮合、账户冻结解冻、手续费、并发交易测试

当前 `src/test/java` 已覆盖认证、用户、行情、交易、资产、通知、管理员、安全、基础设施与并发场景。

## 快速启动

### 1. 准备环境

- JDK 17
- Maven Wrapper
- Node.js 18+
- pnpm
- Docker / Docker Compose

### 2. 配置环境变量

复制 `.env.example` 为本地 `.env`，填写以下关键配置：

- PostgreSQL 密码
- Redis 密码
- RabbitMQ 用户名/密码
- JWT 密钥
- Grafana 管理员密码

### 3. 启动基础设施

```cmd
docker compose -f docker-compose.dev.yml up -d
docker compose -f docker-compose.dev.yml --profile nonprod-app up -d app-1 app-2 nginx
```

如果需要观测栈：

```cmd
docker compose -f docker-compose.dev.yml --profile nonprod-observe up -d
```

### 4. 启动后端

```cmd
call .\mvnw.cmd spring-boot:run
```

### 5. 启动前端

```cmd
cd /d stock-simulation-web
pnpm install
pnpm dev
```

## 常用访问地址

- Swagger UI：`http://localhost/swagger-ui.html`
- OpenAPI：`http://localhost/v3/api-docs`
- Actuator Health：`http://localhost/actuator/health`
- Prometheus：`http://localhost:9090`
- Grafana：`http://localhost:3000`
- RabbitMQ 管理台：`http://localhost:15672`

## 相关文档

- 架构设计：[docs/architecture.md](docs/architecture.md)
- 详细设计：[docs/doc-A-detailed-design.md](docs/doc-A-detailed-design.md)
- 开发路线图：[docs/doc-D-dev-roadmap.md](docs/doc-D-dev-roadmap.md)
- 最新性能报告：[docs/performance-report-2026-04-26.md](docs/performance-report-2026-04-26.md)

## 项目总结

这个项目的价值不只在于完成了一个模拟炒股平台，更在于把一个中大型后端系统常见的关键技术点串成了完整闭环：

- 从业务规则建模到领域服务拆分
- 从缓存、数据库、MQ 到 WebSocket 的全链路设计
- 从功能实现到压测、观测、性能调优与容量评估

如果把它作为求职项目，它最能体现的不是“会写接口”，而是：

- 能独立设计高并发业务系统
- 能识别和治理热点路径
- 能用数据而不是口号证明性能结果
