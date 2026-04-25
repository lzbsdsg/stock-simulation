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

README 中展示的性能口径采用了 **Iteration 17 与 Iteration 18 综合结论中的最优展示方案**：

- HTTP 混合链路能力：采用 **Iteration 18 B 组**
  - 原因：它最接近真实混合业务负载，而且在更高吞吐下仍保持 `0` 失败，最适合代表项目的稳定容量。
- 查询接口族能力：补充 **Iteration 18 common-qps**
  - 原因：能更清楚展示查询类接口整体吞吐与尾延迟。
- WebSocket：采用 **Iteration 17 的链路可用性结论**
  - 原因：这一轮能稳定证明 1000 连接握手、订阅、收消息链路可用；但正式高并发推送时延仍在继续补测，因此 README 不夸大为最终 WS SLA。

### 1. 最适合展示项目 HTTP 性能的结果：Iteration 18 B 组

压测模型：

- `full_chain_mix` 混合业务流量
- `endpoint_market_quote` 单独持续压行情接口
- `endpoint_portfolio_overview` 单独持续压资产总览
- `endpoint_trade_list` 单独持续压订单列表

参数：

- `FULL_CHAIN_RPS=650`
- `QUOTE_RPS=260`
- `PORTFOLIO_RPS=130`
- `TRADE_LIST_RPS=130`

结果：

- 总 HTTP 吞吐：`1816.15 req/s`
- HTTP 失败率：`0`
- 全链路业务 `p95`：`31ms`
- quote-only `p95`：`2.36ms`
- portfolio-overview-only `p95`：`26.17ms`
- trade-list-only `p95`：`5.28ms`

为什么选择它作为 README 主展示结果：

- 吞吐明显高于保守档
- 失败率仍保持 `0`
- 是真实混合业务模型下的稳定容量点
- 比单接口压测更能代表系统整体能力

### 2. 查询接口族补充结果：Iteration 18 common-qps

结果：

- 总 HTTP 吞吐：`1347.39 req/s`
- HTTP 失败率：`0`
- 整体 `p95`：`7.53ms`

关键接口：

- `market_quote`：`219.56 qps`，`p95=2.11ms`
- `portfolio_overview`：`119.75 qps`，`p95=21.38ms`
- `portfolio_positions`：`89.83 qps`，`p95=21.38ms`
- `trade_orders`：`99.81 qps`，`p95=4.99ms`
- `trade_trades`：`79.85 qps`，`p95=5.19ms`

说明：

- 这组结果更适合展示“查询接口整体健康度”
- 也能反映资产类查询相对更重，但仍处于健康范围

### 3. WebSocket 当前可证明能力

当前较稳妥的结论是：

- `1000` 并发连接可建立
- STOMP 建连可成功
- 消息链路可打通并收到推送

说明：

- HTTP 混合链路能力已经有较完整的容量结论
- WebSocket 高并发推送时延样本仍在持续补测，所以 README 只展示已被当前报告稳定支持的能力，不夸大未闭环结论

## 为什么这个项目的性能结论可信

- 压测不是只打单接口，而是同时覆盖混合业务流量与热点查询接口
- 使用 Docker Compose 完整拓扑，而不是单机假环境
- 压测入口统一经过 Nginx，再进入双实例应用与真实中间件
- 报告区分了“请求级吞吐”和“业务级成功”，避免只看 `QPS` 掩盖失败率
- 同时给出稳定区、冲刺区、风险区，不用极限数字误导系统能力

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

```powershell
docker compose -f docker-compose.dev.yml up -d
docker compose -f docker-compose.dev.yml --profile nonprod-app up -d app-1 app-2 nginx
```

如果需要观测栈：

```powershell
docker compose -f docker-compose.dev.yml --profile nonprod-observe up -d
```

### 4. 启动后端

```powershell
.\mvnw.cmd spring-boot:run
```

### 5. 启动前端

```powershell
cd stock-simulation-web
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
- Iteration 17 性能交付：[docs/iteration-17-delivery.md](docs/iteration-17-delivery.md)
- Iteration 18 性能报告：[docs/iteration-18-performance-report.md](docs/iteration-18-performance-report.md)
- 17/18 整合说明：[docs/iteration-17-18-current-performance-consolidated-2026-04-25.md](docs/iteration-17-18-current-performance-consolidated-2026-04-25.md)

## 项目总结

这个项目的价值不只在于完成了一个模拟炒股平台，更在于把一个中大型后端系统常见的关键技术点串成了完整闭环：

- 从业务规则建模到领域服务拆分
- 从缓存、数据库、MQ 到 WebSocket 的全链路设计
- 从功能实现到压测、观测、性能调优与容量评估

如果把它作为求职项目，它最能体现的不是“会写接口”，而是：

- 能独立设计高并发业务系统
- 能识别和治理热点路径
- 能用数据而不是口号证明性能结果
