# 股市仿真交易系统 — 架构文档

> 版本：2.0 | 日期：2026-02-13 | 作者：Copilot Architect
> 变更：v2.0 覆盖式整合高并发架构设计

---

## 一、系统概览

股市仿真交易系统是一个面向个人投资者学习与练习的模拟炒股平台。系统接入 A 股实时行情（允许数秒延迟），用户可使用虚拟资金按照真实 A 股规则进行模拟买卖，系统提供持仓管理、收益分析、自选股等功能。

**系统设计目标支撑百万注册用户、日活峰值 10 万、同时在线 5 万的高并发场景。**

### 1.1 核心目标

- **仿真交易**：尽可能还原 A 股交易规则（T+1、涨跌停、手续费、最小交易单位）
- **实时行情**：接入第三方数据源，支持快照 + K 线展示
- **资产管理**：实时持仓、浮动盈亏、收益曲线、资金流水
- **安全可靠**：资金操作强一致、防刷防作弊
- **高并发高可用**：支撑百万用户规模，核心链路 P99 < 200ms，可用性 ≥ 99.9%

### 1.2 SLO（Service Level Objectives）

| 指标 | 目标 | 测量方式 |
|---|---|---|
| 可用性 | ≥ 99.9%（月度） | Prometheus uptime |
| 行情读接口 P95 | < 50ms | Micrometer histogram |
| 行情读接口 P99 | < 100ms | Micrometer histogram |
| 交易写接口 P95 | < 100ms | Micrometer histogram |
| 交易写接口 P99 | < 200ms | Micrometer histogram |
| 撮合延迟 P99 | < 100ms | 自定义 Timer |
| WebSocket 推送延迟 | < 500ms（端到端） | 客户端上报 |
| 行情 Provider 降级自动切换 | < 3s | 断路器状态 |
| 下单吞吐量 | ≥ 3000 TPS（峰值） | k6 压测 |
| 行情推送吞吐 | ≥ 5 万连接 × 3s/次 | 压测 |
| 错误率 | < 0.1%（非业务错误） | 5xx / total |

### 1.3 非目标

- 不影响真实市场（纯模拟）
- 不支持融资融券、期权、期货
- 不支持量化策略自动交易（MVP阶段）
- 不支持多市场（仅A股）

---

## 二、技术架构

### 2.1 高并发总体架构图

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                              用户浏览器 / 移动端                              │
│         Vue 3 + TypeScript + Vite + ECharts + WebSocket Client              │
└──────────────────┬─────────────────────────┬─────────────────────────────────┘
                   │ HTTP/REST                │ WS/STOMP
                   ▼                          ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                  Nginx (反向代理 + 负载均衡 + 静态资源 + 限流)                 │
│    ┌─────────┐       upstream: round-robin / least_conn                     │
│    │ WAF/限流 │       limit_req_zone $binary_remote_addr 10m rate=100r/s    │
│    └─────────┘       WebSocket sticky (ip_hash)                             │
└──────────┬──────────────────────┬────────────────────────────────────────────┘
           │                      │
     ┌─────┴──────┐         ┌────┴───────┐
     ▼            ▼         ▼            ▼
┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐
│ App-1   │ │ App-2   │ │ App-3   │ │ App-N   │    ← Spring Boot 多实例 (水平扩展)
│(Trade)  │ │(Market) │ │(General)│ │ ...     │
└────┬────┘ └────┬────┘ └────┬────┘ └────┬────┘
     │           │           │           │
     ├───────────┴───────────┴───────────┘
     │
     ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                         多级缓存层                                           │
│  ┌──────────────────┐    ┌──────────────────────────────────┐               │
│  │ L1: Caffeine     │    │ L2: Redis Cluster (3主3从)        │               │
│  │ JVM本地缓存       │───→│ 行情快照/限流/JWT黑名单/幂等       │               │
│  │ 行情快照 TTL=3s   │    │ Pub/Sub 行情扇出                  │               │
│  │ 股票信息 TTL=5min │    │ Stream 撮合消息队列               │               │
│  └──────────────────┘    └──────────────────────────────────┘               │
└──────────────────────────────────┬───────────────────────────────────────────┘
                                   │
     ┌─────────────────────────────┼─────────────────────────────┐
     │                             │                             │
     ▼                             ▼                             ▼
┌──────────────┐         ┌──────────────┐              ┌──────────────┐
│ PostgreSQL   │         │  RabbitMQ    │              │ Prometheus + │
│ 主 (写)      │         │  3.13        │              │ Grafana +    │
│ ├→ 从1 (读)  │         │ 成交事件     │              │ Loki (日志)  │
│ └→ 从2 (读)  │         │ 通知事件     │              │ Tempo(链路)  │
│ 分区: 流水/快照│         │ 邮件事件     │              └──────────────┘
│ 日K: 按需入库  │
└──────────────┘         └──────────────┘
```

### 2.2 分层架构（DDD-lite 四层）

```
┌─────────────────────────────────────────────┐
│  Controller 层（接口适配）                     │
│  - REST API 端点                             │
│  - 参数校验 (@Valid)                          │
│  - DTO/VO 转换                               │
│  - 统一 Result<T> 封装                        │
├─────────────────────────────────────────────┤
│  Application 层（应用服务/用例编排）            │
│  - 编排领域服务 + 基础设施                     │
│  - 事务边界 (@Transactional)                  │
│  - Command/Query 对象                        │
│  - 发布领域事件                               │
├─────────────────────────────────────────────┤
│  Domain 层（核心业务逻辑）                     │
│  - Entity（纯POJO，无框架依赖）               │
│  - Domain Service（业务规则）                 │
│  - Value Object（不可变值对象）               │
│  - Repository 接口（仅定义）                  │
├─────────────────────────────────────────────┤
│  Infrastructure 层（技术实现）                 │
│  - Repository 实现 (MyBatis-Plus)            │
│  - Gateway 实现 (Redis/MQ/HTTP)              │
│  - 外部服务适配器 (行情Provider)              │
│  - 多级缓存实现 (Caffeine + Redis)           │
│  - 配置类                                    │
└─────────────────────────────────────────────┘
```

---

## 三、高并发关键设计

### 3.1 多级缓存架构

```
请求 → L1 Caffeine (JVM本地, 毫秒级) → L2 Redis (分布式, 亚毫秒) → DB/Provider

┌────────────────────────────────────────────────────────────────────┐
│  缓存数据      │ L1 Caffeine          │ L2 Redis                  │
├────────────────┼──────────────────────┼───────────────────────────┤
│  行情快照       │ TTL=3s, max=5000     │ TTL=5s+random(0,500ms)   │
│  K线数据        │ —                    │ 不走 Redis 缓存           │
│  股票基础信息   │ TTL=5min, max=10000  │ TTL=30min                │
│  用户Session    │ —                    │ TTL=30min (JWT验证)       │
│  手续费配置     │ TTL=10min, max=10    │ TTL=30min                │
│  交易时间配置   │ TTL=10min, max=10    │ TTL=30min                │
└────────────────┴──────────────────────┴───────────────────────────┘
```

**缓存一致性策略**：
- **行情数据**（天然有 TTL，不需要主动失效）：写入时同时更新 L2，L1 由 TTL 自然过期
- **配置数据**（低频变更）：变更时通过 Redis Pub/Sub 通知所有实例失效 L1
- **防雪崩**：TTL 添加随机偏移 `TTL + random(0, TTL*0.1)`
- **防穿透**：空值缓存 30s + Bloom Filter（股票代码校验）
- **防击穿**：Caffeine `refreshAfterWrite` 异步刷新 + Redis 分布式锁 `SETNX` 单实例回源

**历史 K 线策略（2026-03-25 更新）**：
- 历史日K使用真实外部数据源，按股票按需同步并写入 PostgreSQL。
- 同一股票在同一自然日仅同步一次（状态表防重）。
- 周K/月K由日K聚合生成，不单独缓存。
- 仅保留最近 3 年日K，访问时增量补齐并滚动清理旧数据。

### 3.2 行情推送扇出架构

```
行情 Provider (Sina/Tencent)
        │
        ▼ (单线程拉取，3s/轮)
┌───────────────────────┐
│  MarketIngestService  │  ← 单实例负责拉取（分布式锁选主）
│  拉取 → 写Redis L2    │
│  → Pub/Sub 广播       │
└───────┬───────────────┘
        │ Redis Pub/Sub channel: market:quote:broadcast
        │
   ┌────┴────┬────────┬────────┐
   ▼         ▼        ▼        ▼
┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐
│App-1 │ │App-2 │ │App-3 │ │App-N │  ← 每个实例订阅 Pub/Sub
│更新L1│ │更新L1│ │更新L1│ │更新L1│
│推送WS│ │推送WS│ │推送WS│ │推送WS│  ← 每个实例只推送自己管理的WS连接
└──────┘ └──────┘ └──────┘ └──────┘
```

**关键约束**：
- 行情拉取节点通过 Redis 分布式锁 `SETNX market:ingest:lock TTL=10s` 选主，避免重复拉取
- 每个 App 实例维护自己的 WebSocket 连接集合，只推送本地连接
- WebSocket 连接上限：单实例 ≤ 10000 连接，超限拒绝新连接并返回 503
- 推送背压：消息队列满时丢弃旧消息（行情数据非关键，允许丢失）

### 3.2.1 可见股票集合驱动的实时调度（2026-04 更新）

为保障高并发长尾访问场景下的实时性，行情抓取从“固定股票池”升级为“前端可见集合驱动”。

```
前端页面可见股票（列表/详情/自选）
   -> POST /api/v1/market/visible-codes (1.5s 心跳)
   -> Redis ZSet: market:active:quotes (score=lastSeenTs)
   -> MarketIngestService 每 1s 执行：
      1) active-window 内活跃股票优先批量拉取
      2) round-robin 全市场轮巡补齐，避免冷门股票长期不更新
   -> 写 L2 + 广播 Pub/Sub + 各实例推送 WS
```

**调度策略**：
- 活跃优先：默认 active-window=8s 内被上报的股票优先拉取。
- 轮巡兜底：默认每轮补齐 100 只全市场轮巡股票，避免缓存长期冷启动。
- 预算保护：通过 `active-batch-size` 与 `round-robin-batch-size` 控制单轮抓取上限。

**默认参数**：
- `market.ingest.pull-interval-ms=1000`
- `market.ingest.active-window-ms=8000`
- `market.ingest.active-batch-size=800`
- `market.ingest.round-robin-batch-size=100`

**SLO 目标（可见股票集合）**：
- 可见股票更新周期：1~2 秒。
- WebSocket 推送端到端延迟 P99 < 500ms。
- 行情读接口 P95/P99: < 50ms / < 100ms。

### 3.2.2 行情实时观测接口（2026-04 更新）

为避免仅依赖 Prometheus 看板排障，新增业务侧直读接口：

- `GET /api/v1/market/realtime-metrics`
- 用途：一跳查询行情链路关键延迟与运行状态，便于联调、灰度和验收。

**返回核心指标**：
- 活跃池规模：`activeCodeCount`（active-window 内可见股票数）
- 抓取状态：`lastIngestCodeCount`、`lastPublishedQuoteCount`、`lastIngestDurationMs`
- WS 状态：`wsActiveConnections`、`wsQueuedTasks`、`wsDegradedMode`、`wsDroppedTotal`
- 延迟分项：
   - `ingestCycleLatency`（`market.ingest.cycle.duration`）
   - `pubSubFanoutLatency`（`market.pubsub.fanout.delay`）
   - `wsQueueLatency`（`market.ws.queue.delay`）
   - `wsPushLatency`（`ws_push_duration_seconds`）

**延迟字段定义**：
- `count`：样本数
- `meanMs`：平均耗时(ms)
- `maxMs`：最大耗时(ms)
- `p95Ms` / `p99Ms`：若已启用分位统计则返回，否则为 `null`

### 3.2.3 行情中心分页与榜单策略（2026-04 更新）

```
MarketPage
   -> 加载股票池（/market/listed，多批次拉全量）
   -> 本地分页切片（30/40 每页）
   -> 当前页股票集合写入 marketPageCodes
   -> 展示口径：行情页显示当前页，仪表盘显示自选股
   -> 实时口径：自选股 + 当前页 + 热点集合（并集去重）
   -> WS 按并集订阅，切页仅更新当前页来源集合

大盘/榜单
   -> 后端代理官方源（/market/indexes + /market/rank-board）
   -> 官方失败时并发回源新浪/腾讯等 Provider 并按融合策略选优
   -> 若多源仍不可用，回退最近一次成功缓存（后端）
```

**职责边界**：
- 后端负责官方源聚合与稳定性兜底，前端不再直连官方榜单/指数接口。
- 后端统一执行“新鲜度 + 完整度 + 有效变化”融合，避免旧数据晚到覆盖新数据。
- 前端分页固定本地执行，降低后端分页/排序计算压力。
- 展示集合与实时集合解耦，避免“切页”影响用户自选实时体验。

### 3.3 读写分离

```
写操作 (INSERT/UPDATE/DELETE)
  → @Transactional → HikariCP 主库连接池 → PG Primary
  → WAL 流复制 → PG Standby-1, Standby-2

读操作 (SELECT)
  → @ReadOnly / 自定义 @SlaveDataSource 注解
  → HikariCP 从库连接池 → PG Standby (轮询)
  → 复制延迟 < 100ms（监控告警）
```

**实现方式**：
- Spring `AbstractRoutingDataSource` + 自定义 `@ReadOnly` 注解
- 主库连接池：`maximumPoolSize=30, minimumIdle=10`
- 从库连接池：`maximumPoolSize=50, minimumIdle=20`（读多写少）
- 流复制延迟监控：`pg_stat_replication.replay_lag` > 1s 告警

### 3.4 交易写路径优化

```
下单请求 → Nginx 限流(10/min/user) → JWT校验
  → 幂等检查 (Redis SETNX, O(1))
  → Caffeine 热点配置读取 (交易时间/涨跌停)
  → 行情缓存读取 (L1 Caffeine, 3s内有效)
  → 开启DB事务（仅在此处访问DB）:
      SELECT ... FOR UPDATE Account  ←  行级锁，不锁表
      UPDATE Account
      INSERT Order
      INSERT FundFlow
  → 提交事务
  → 异步发送撮合消息 (RabbitMQ / Redis Stream)
```

**优化要点**：
- 事务内只做最少DB操作（验证、扣款、建单），校验前置到缓存层
- `FOR UPDATE` 锁粒度精确到用户级别（行锁），不同用户完全并行
- 连接池预热：应用启动时填充 `minimumIdle` 连接
- 乐观锁重试：`version` 冲突最多 3 次，间隔 50ms × 2^n 指数退避

### 3.5 WebSocket 连接管理

```
┌──────────────────────────────────────────────────┐
│  WebSocket 连接管理                               │
│  ┌─────────────────────────────────────────────┐ │
│  │ 连接注册表 (ConcurrentHashMap)               │ │
│  │ Key: userId → Set<WebSocketSession>         │ │
│  │ 总连接数监控 → Prometheus gauge              │ │
│  └─────────────────────────────────────────────┘ │
│  ┌─────────────────────────────────────────────┐ │
│  │ 背压控制                                     │ │
│  │ - 发送缓冲区 > 64KB → 跳过本次推送            │ │
│  │ - 连续3次跳过 → 断开连接 + 日志告警           │ │
│  │ - 推送线程池: core=4, max=8, queue=1000     │ │
│  └─────────────────────────────────────────────┘ │
│  ┌─────────────────────────────────────────────┐ │
│  │ 心跳与重连                                   │ │
│  │ - 服务端心跳: 30s                            │ │
│  │ - 客户端心跳: 25s                            │ │
│  │ - 无心跳60s → 服务端主动断开                  │ │
│  │ - 客户端断线重连: 指数退避 1s/2s/4s/8s/30s   │ │
│  └─────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────┘
```

### 3.6 异步处理管线

```
┌─────────────┐     ┌──────────────┐     ┌─────────────────┐
│  下单事件    │────→│  RabbitMQ    │────→│  MatchConsumer  │
│ (Producer)  │     │ exchange:    │     │  撮合成交        │
│             │     │ trade.match  │     │  并发消费者=8    │
└─────────────┘     └──────────────┘     └─────────────────┘

┌─────────────┐     ┌──────────────┐     ┌─────────────────┐
│  成交事件    │────→│  RabbitMQ    │────→│  多个Consumer   │
│ (Publisher) │     │ exchange:    │     │  ├ 通知推送      │
│             │     │ trade.filled │     │  ├ 站内信写入    │
│             │     │ (fanout)     │     │  └ 异步对账      │
└─────────────┘     └──────────────┘     └─────────────────┘

┌─────────────┐     ┌──────────────┐     ┌─────────────────┐
│  邮件事件    │────→│  RabbitMQ    │────→│  EmailConsumer  │
│ (OTP/通知)  │     │ exchange:    │     │  SMTP发送        │
│             │     │ notification │     │  失败 → 延迟重试 │
└─────────────┘     └──────────────┘     └─────────────────┘
```

**MQ 配置要点**：
- `prefetch=10`：每个消费者预拉取 10 条
- 消息持久化 + 手动 ACK
- DLQ（死信队列）：3 次重试失败 → 死信 → 人工处理
- 消费者幂等：通过 `clientOrderId` 保证撮合幂等

---

## 四、模块边界与依赖

### 4.1 模块清单

| 模块 | 包名 | 职责 | 核心实体 |
|---|---|---|---|
| **common** | `common` | 统一响应、异常、错误码、工具、多级缓存抽象 | Result, ErrorCode, BizException |
| **config** | `config` | 全局配置（Security/Redis/WS/Swagger/Caffeine/DataSource路由） | — |
| **auth** | `auth` | 认证授权（注册/登录/JWT/OTP） | User, OtpRecord |
| **user** | `user` | 用户信息、账户管理 | UserProfile, Account |
| **market** | `market` | 行情接入、多级缓存、Pub/Sub 扇出推送 | QuoteSnapshot, KLinePoint, StockInfo |
| **trade** | `trade` | 委托下单、撮合成交、撤单 | Order, Trade |
| **portfolio** | `portfolio` | 持仓、资金流水、资产快照、收益 | Position, FundFlow, AssetSnapshot |
| **watchlist** | `watchlist` | 自选股管理 | WatchlistItem |
| **notification** | `notification` | 消息推送（WS/站内信） | NotificationMessage |
| **admin** | `admin` | 管理后台（用户管理/系统配置） | — |

### 4.2 模块依赖图

```
auth ──→ user（查询/创建用户）
trade ──→ user（扣减/冻结资金）
trade ──→ market（获取行情判断成交）
trade ──→ portfolio（更新持仓）
portfolio ──→ user（读取账户）
portfolio ──→ market（计算实时市值）
watchlist ──→ market（关联行情推送）
notification ──→ trade（消费成交事件）
notification ──→ market（推送行情）
admin ──→ user, trade（管理查询）
```

**硬性约束**：跨模块调用只通过 Application Service 接口，不直接引用另一模块的 Domain 层。

---

## 五、后端代码结构

```
stock-simulation/
├── pom.xml
├── Dockerfile
├── docker-compose.dev.yml
├── src/main/java/com/lzbsdsg/stocksimulation/
│   ├── StockSimulationApplication.java
│   ├── common/
│   │   ├── result/
│   │   │   ├── Result.java
│   │   │   └── PageResult.java
│   │   ├── exception/
│   │   │   ├── BizException.java
│   │   │   ├── ErrorCode.java
│   │   │   └── GlobalExceptionHandler.java
│   │   ├── annotation/
│   │   │   ├── RateLimit.java                 # 限流注解
│   │   │   └── ReadOnly.java                  # 从库路由注解
│   │   ├── aspect/
│   │   │   └── RateLimitAspect.java
│   │   ├── cache/
│   │   │   ├── MultiLevelCache.java           # 多级缓存抽象
│   │   │   ├── MultiLevelCacheManager.java    # Caffeine L1 + Redis L2 管理器
│   │   │   └── CacheInvalidateListener.java   # Redis Pub/Sub 缓存失效监听
│   │   └── util/
│   │       ├── JsonUtil.java
│   │       ├── BigDecimalUtil.java
│   │       └── TraceIdUtil.java
│   ├── config/
│   │   ├── SecurityConfig.java
│   │   ├── JwtAuthenticationFilter.java
│   │   ├── WebSocketConfig.java
│   │   ├── RedisConfig.java                   # Redis Cluster + Pub/Sub
│   │   ├── CaffeineConfig.java                # Caffeine 本地缓存配置
│   │   ├── DataSourceRoutingConfig.java        # 读写分离数据源路由
│   │   ├── RabbitMQConfig.java
│   │   ├── SwaggerConfig.java
│   │   ├── CorsConfig.java
│   │   ├── MyBatisPlusConfig.java
│   │   ├── AsyncConfig.java                   # 异步线程池配置
│   │   ├── WebSocketBrokerConfig.java         # STOMP Broker 集群配置
│   │   └── TradeRuleConfig.java
│   ├── auth/
│   │   ├── controller/
│   │   │   └── AuthController.java
│   │   ├── application/
│   │   │   ├── AuthApplicationService.java
│   │   │   ├── command/
│   │   │   │   ├── RegisterCommand.java
│   │   │   │   ├── LoginCommand.java
│   │   │   │   ├── LoginByOtpCommand.java
│   │   │   │   ├── SendOtpCommand.java
│   │   │   │   ├── RefreshTokenCommand.java
│   │   │   │   └── ResetPasswordCommand.java
│   │   │   └── dto/
│   │   │       └── TokenDTO.java
│   │   ├── domain/
│   │   │   ├── entity/
│   │   │   │   └── User.java
│   │   │   ├── service/
│   │   │   │   ├── OtpDomainService.java
│   │   │   │   └── PasswordDomainService.java
│   │   │   └── repository/
│   │   │       └── UserRepository.java
│   │   └── infrastructure/
│   │       ├── persistence/
│   │       │   ├── UserMapper.java
│   │       │   ├── UserDO.java
│   │       │   └── UserRepositoryImpl.java
│   │       ├── gateway/
│   │       │   ├── OtpRedisGateway.java
│   │       │   ├── JwtTokenProvider.java
│   │       │   └── EmailGateway.java
│   │       └── converter/
│   │           └── UserConverter.java
│   ├── user/
│   │   ├── controller/
│   │   │   └── UserController.java
│   │   ├── application/
│   │   │   ├── UserApplicationService.java
│   │   │   └── AccountApplicationService.java
│   │   ├── domain/
│   │   │   ├── entity/
│   │   │   │   ├── UserProfile.java
│   │   │   │   └── Account.java
│   │   │   ├── service/
│   │   │   │   └── AccountDomainService.java
│   │   │   └── repository/
│   │   │       ├── UserProfileRepository.java
│   │   │       └── AccountRepository.java
│   │   └── infrastructure/
│   │       └── persistence/
│   │           ├── AccountMapper.java
│   │           ├── AccountDO.java
│   │           └── AccountRepositoryImpl.java
│   ├── market/
│   │   ├── controller/
│   │   │   └── MarketController.java
│   │   ├── application/
│   │   │   ├── MarketApplicationService.java
│   │   │   └── dto/
│   │   │       ├── QuoteVO.java
│   │   │       └── KLineVO.java
│   │   ├── domain/
│   │   │   ├── entity/
│   │   │   │   ├── StockInfo.java
│   │   │   │   └── QuoteSnapshot.java
│   │   │   ├── valueobject/
│   │   │   │   ├── KLinePoint.java
│   │   │   │   └── KLinePeriod.java
│   │   │   ├── service/
│   │   │   │   └── MarketDataFacade.java       # 多级缓存+降级+节流编排
│   │   │   └── repository/
│   │   │       └── StockInfoRepository.java
│   │   └── infrastructure/
│   │       ├── provider/
│   │       │   ├── MarketDataProvider.java
│   │       │   ├── SinaMarketDataAdapter.java
│   │       │   ├── TencentMarketDataAdapter.java
│   │       │   └── MockMarketDataAdapter.java
│   │       ├── gateway/
│   │       │   └── MarketCacheGateway.java      # 多级缓存实现
│   │       ├── ingest/
│   │       │   ├── MarketIngestService.java     # 行情拉取主节点(分布式锁选主)
│   │       │   └── MarketPubSubListener.java    # Pub/Sub 订阅 → 更新L1 + 推送WS
│   │       ├── websocket/
│   │       │   ├── MarketWebSocketHandler.java  # WS连接管理(注册表+背压)
│   │       │   └── MarketPushScheduler.java     # 定时推送自选股行情(3s)
│   │       └── persistence/
│   │           ├── StockInfoMapper.java
│   │           └── StockInfoRepositoryImpl.java
│   ├── trade/
│   │   ├── controller/
│   │   │   └── OrderController.java
│   │   ├── application/
│   │   │   ├── TradeApplicationService.java
│   │   │   ├── command/
│   │   │   │   ├── PlaceOrderCommand.java
│   │   │   │   └── CancelOrderCommand.java
│   │   │   └── dto/
│   │   │       ├── OrderVO.java
│   │   │       └── TradeVO.java
│   │   ├── domain/
│   │   │   ├── entity/
│   │   │   │   ├── Order.java
│   │   │   │   └── Trade.java
│   │   │   ├── valueobject/
│   │   │   │   ├── OrderSide.java
│   │   │   │   ├── OrderStatus.java
│   │   │   │   └── OrderType.java
│   │   │   ├── service/
│   │   │   │   ├── OrderDomainService.java
│   │   │   │   ├── MatchEngine.java
│   │   │   │   └── FeeCalculator.java
│   │   │   └── repository/
│   │   │       ├── OrderRepository.java
│   │   │       └── TradeRepository.java
│   │   └── infrastructure/
│   │       ├── persistence/
│   │       │   ├── OrderMapper.java
│   │       │   ├── OrderDO.java
│   │       │   ├── OrderRepositoryImpl.java
│   │       │   ├── TradeMapper.java
│   │       │   ├── TradeDO.java
│   │       │   └── TradeRepositoryImpl.java
│   │       ├── gateway/
│   │       │   └── IdempotencyGateway.java
│   │       └── mq/
│   │           ├── OrderMessageProducer.java
│   │           └── MatchConsumer.java            # prefetch=10, 并发消费者=8
│   ├── portfolio/
│   │   ├── controller/
│   │   │   └── PortfolioController.java
│   │   ├── application/
│   │   │   ├── PortfolioApplicationService.java
│   │   │   └── dto/
│   │   │       ├── OverviewVO.java
│   │   │       ├── PositionVO.java
│   │   │       └── FundFlowVO.java
│   │   ├── domain/
│   │   │   ├── entity/
│   │   │   │   ├── Position.java
│   │   │   │   ├── FundFlow.java
│   │   │   │   └── AssetSnapshot.java
│   │   │   ├── service/
│   │   │   │   ├── PositionDomainService.java
│   │   │   │   └── AssetSnapshotService.java
│   │   │   └── repository/
│   │   │       ├── PositionRepository.java
│   │   │       ├── FundFlowRepository.java
│   │   │       └── AssetSnapshotRepository.java
│   │   └── infrastructure/
│   │       ├── persistence/
│   │       │   ├── PositionMapper.java
│   │       │   ├── PositionDO.java
│   │       │   ├── PositionRepositoryImpl.java
│   │       │   ├── FundFlowMapper.java
│   │       │   ├── FundFlowDO.java
│   │       │   └── AssetSnapshotMapper.java
│   │       └── scheduler/
│   │           └── AssetSnapshotScheduler.java   # 分批处理(每批500用户)
│   ├── watchlist/
│   │   ├── controller/
│   │   │   └── WatchlistController.java
│   │   ├── application/
│   │   │   └── WatchlistApplicationService.java
│   │   ├── domain/
│   │   │   ├── entity/
│   │   │   │   └── WatchlistItem.java
│   │   │   └── repository/
│   │   │       └── WatchlistRepository.java
│   │   └── infrastructure/
│   │       └── persistence/
│   │           ├── WatchlistMapper.java
│   │           └── WatchlistRepositoryImpl.java
│   ├── notification/
│   │   ├── controller/
│   │   │   └── NotificationController.java
│   │   ├── application/
│   │   │   └── NotificationApplicationService.java
│   │   ├── domain/
│   │   │   ├── entity/
│   │   │   │   └── NotificationMessage.java
│   │   │   └── repository/
│   │   │       └── NotificationRepository.java
│   │   └── infrastructure/
│   │       ├── mq/
│   │       │   └── TradePushConsumer.java
│   │       └── persistence/
│   │           └── NotificationMapper.java
│   └── admin/
│       ├── controller/
│       │   └── AdminController.java
│       └── application/
│           └── AdminApplicationService.java
├── src/main/resources/
│   ├── application.yml
│   ├── application-dev.yml
│   ├── application-test.yml
│   ├── logback-spring.xml
│   └── db/migration/
│       ├── V20260213_001__create_user_tables.sql
│       ├── V20260213_002__create_account_table.sql
│       ├── V20260213_003__create_stock_info_table.sql
│       ├── V20260213_004__create_trade_tables.sql
│       ├── V20260213_005__create_portfolio_tables.sql
│       ├── V20260213_006__create_watchlist_table.sql
│       ├── V20260213_007__create_notification_table.sql
│       └── V20260214_001__add_table_partitions.sql   # 流水/快照表月分区
├── src/test/java/com/lzbsdsg/stocksimulation/
│   ├── auth/
│   │   ├── domain/service/OtpDomainServiceTest.java
│   │   ├── application/AuthApplicationServiceTest.java
│   │   └── controller/AuthControllerApiTest.java
│   ├── trade/
│   │   ├── domain/service/OrderDomainServiceTest.java
│   │   ├── domain/service/MatchEngineTest.java
│   │   ├── domain/service/FeeCalculatorTest.java
│   │   ├── application/TradeApplicationServiceTest.java
│   │   └── controller/OrderControllerApiTest.java
│   ├── portfolio/
│   │   └── domain/service/PositionDomainServiceTest.java
│   ├── integration/
│   │   ├── TradeIntegrationTest.java
│   │   └── AuthIntegrationTest.java
│   └── performance/
│       └── TradeConcurrencyTest.java              # 并发下单压力测试
├── k6/
│   ├── trade-load-test.js                         # 下单接口压测脚本
│   ├── market-load-test.js                        # 行情接口压测脚本
│   └── websocket-load-test.js                     # WS连接压测脚本
├── nginx/
│   └── nginx.conf                                 # 含限流+负载均衡+sticky
├── prometheus/
│   ├── prometheus.yml
│   └── alert-rules.yml                            # 告警规则
└── grafana/
    └── dashboards/
        ├── jvm-dashboard.json
        ├── http-dashboard.json
        ├── trade-dashboard.json                   # 交易业务指标
        └── market-dashboard.json                  # 行情推送指标
```

---

## 六、前端代码结构

```
stock-simulation-web/
├── package.json
├── pnpm-lock.yaml
├── vite.config.ts
├── tsconfig.json
├── tsconfig.node.json
├── env.d.ts
├── .eslintrc.cjs
├── .prettierrc.json
├── index.html
├── public/
│   └── favicon.ico
├── src/
│   ├── main.ts
│   ├── App.vue
│   ├── api/
│   │   ├── request.ts               # Axios 实例 + 拦截器(401 refresh)
│   │   ├── auth.ts
│   │   ├── market.ts
│   │   ├── trade.ts
│   │   ├── portfolio.ts
│   │   ├── watchlist.ts
│   │   └── notification.ts
│   ├── assets/
│   │   └── styles/
│   │       ├── variables.scss
│   │       └── global.scss
│   ├── components/
│   │   ├── common/
│   │   │   ├── AppHeader.vue
│   │   │   ├── AppSidebar.vue
│   │   │   └── LoadingSpinner.vue
│   │   ├── market/
│   │   │   ├── KLineChart.vue
│   │   │   ├── QuoteCard.vue
│   │   │   ├── StockSearch.vue
│   │   │   └── MarketOverview.vue
│   │   ├── trade/
│   │   │   ├── OrderForm.vue
│   │   │   ├── OrderList.vue
│   │   │   └── TradeHistory.vue
│   │   └── portfolio/
│   │       ├── AssetOverview.vue
│   │       ├── PositionTable.vue
│   │       ├── EquityCurve.vue
│   │       └── FundFlowTable.vue
│   ├── composables/
│   │   ├── useWebSocket.ts           # WS连接管理(指数退避重连+背压检测)
│   │   ├── useCountdown.ts
│   │   ├── usePageTitle.ts
│   │   └── useAuth.ts
│   ├── layouts/
│   │   ├── DefaultLayout.vue
│   │   └── AuthLayout.vue
│   ├── pages/
│   │   ├── auth/
│   │   │   ├── LoginPage.vue
│   │   │   ├── RegisterPage.vue
│   │   │   └── ForgotPasswordPage.vue
│   │   ├── dashboard/
│   │   │   └── DashboardPage.vue
│   │   ├── market/
│   │   │   ├── MarketPage.vue
│   │   │   └── StockDetailPage.vue
│   │   ├── trade/
│   │   │   └── TradePage.vue
│   │   ├── portfolio/
│   │   │   └── PortfolioPage.vue
│   │   ├── watchlist/
│   │   │   └── WatchlistPage.vue
│   │   └── settings/
│   │       └── SettingsPage.vue
│   ├── router/
│   │   └── index.ts
│   ├── stores/
│   │   ├── auth.ts
│   │   ├── market.ts                 # useMarketStore (含WS + 断线重连)
│   │   ├── trade.ts
│   │   ├── portfolio.ts
│   │   └── watchlist.ts
│   ├── types/
│   │   ├── api.d.ts
│   │   ├── market.d.ts
│   │   ├── trade.d.ts
│   │   └── portfolio.d.ts
│   └── utils/
│       ├── format.ts
│       ├── constants.ts
│       └── validators.ts
├── tests/
│   ├── unit/
│   │   ├── stores/auth.spec.ts
│   │   └── utils/format.spec.ts
│   └── e2e/
│       └── trade-flow.spec.ts
└── playwright.config.ts
```

---

## 七、关键流程

### 7.1 用户注册

```
1. 用户提交邮箱 → 后端校验格式 + Redis 频率限制(Lua原子操作)
2. 生成6位OTP → BCrypt Hash → 存Redis(TTL=5min)
3. 发送邮件（MQ异步，不阻塞主线程）
4. 用户提交 OTP + 密码 → 验证OTP → 创建User + Account（初始资金）
5. 签发 JWT Access + Refresh Token
```

### 7.2 密码登录

```
1. 用户提交 email + password
2. Caffeine 缓存检查锁定状态（减少DB查询）
3. 查询 User → BCrypt 验证
4. 成功：清零失败计数 → 签发 JWT
5. 失败：失败计数+1 → 达到5次则锁定30min → 写入 Caffeine 缓存
```

### 7.3 获取行情（多级缓存路径）

```
1. 前端请求 GET /market/quote/{code}
2. Controller → MarketDataFacade
3. L1 Caffeine 检查（TTL=3s, 命中率期望 > 80%）
   命中 → 直接返回（< 1ms）
4. L2 Redis 检查（TTL=5s）
   命中 → 回填L1 → 返回（< 5ms）
5. 回源 → 主Provider（Sina）→ 成功 → 写L1+L2 → 返回
6. 主Provider失败 → 断路器检查 → 备Provider（Tencent）
7. 全部失败 → 读取 Redis stale 缓存 → 返回(标记stale)
8. 无任何缓存 → 返回错误码 MARKET_DATA_UNAVAILABLE
```

### 7.4 买入下单（高并发优化路径）

```
1. Nginx 限流通过（10/min/user, 令牌桶）
2. JWT 校验（Redis 黑名单检查）
3. 幂等检查：clientOrderId Redis SETNX(5min) — O(1)
4. 前置校验（全部走缓存，不访问DB）：
   a. 交易时间 ✓ — Caffeine 缓存
   b. 涨跌停 ✓ — Caffeine 行情缓存
   c. 100股整数倍 ✓ — 纯计算
5. 计算冻结金额 = 委托价 × 数量 + 预估手续费
6. 开启DB事务（短事务，目标 < 50ms）：
   a. SELECT ... FOR UPDATE 只锁当前用户 Account 行
   b. 校验 available_balance ≥ 冻结金额
   c. UPDATE Account: available -= 冻结, frozen += 冻结
   d. INSERT Order(status=PENDING)
   e. INSERT FundFlow(type=FREEZE)
7. 提交事务
8. 异步发送 RabbitMQ 撮合消息（不等待结果）
```

### 7.5 撮合成交

```
1. MQ Consumer 获取撮合消息（并发消费者=8, prefetch=10）
2. 幂等检查：orderId 是否已撮合
3. 获取最新行情价（L1 Caffeine → L2 Redis → Provider）
4. 判断是否可成交（买：委托价 ≥ 市价，卖：委托价 ≤ 市价）
5. 开启DB事务：
   a. 计算实际手续费
   b. 生成 Trade 记录
   c. 更新 Order(status=FILLED) — 乐观锁 version
   d. 更新 Position（加持仓/减持仓 + 重算成本价）— 乐观锁
   e. 更新 Account（解冻 + 扣费/入账）— 乐观锁
   f. 插入 FundFlow(type=TRADE)
6. 提交事务
7. 发送成交事件 MQ（fanout → 通知/站内信/对账）
8. 乐观锁冲突 → 重试（最多3次, 50ms × 2^n）
```

### 7.6 每日收盘结算（分批处理）

```
1. 15:00 定时任务触发（Quartz, 分布式锁防重复执行）
2. 过期订单处理：分批查询 PENDING 订单(每批200) → EXPIRED → 解冻资金
3. T+1 冻结更新：分批更新持仓 frozen_until
4. 历史订单归档：03:30 分批迁移终态订单(每批500)
   a. 条件：CANCELLED/EXPIRED/REJECTED 且无成交明细
   b. 搬迁：t_trade_order → t_trade_order_archive
   c. 查询：历史委托统一合并主表 + 归档表
5. 资产快照：分批处理用户(每批500)
   a. 查询用户账户 + 持仓
   b. 批量获取行情（batchGetQuotes, 缓存优先）
   c. 计算总资产、收益率
   d. 批量INSERT AssetSnapshot（幂等：UNIQUE(user_id, snapshot_date)）
6. 全程异步执行，不阻塞 Web 请求线程
```

---

## 八、数据库设计概览

### 8.1 核心表

| 表名 | 所属模块 | 说明 | 高并发策略 |
|---|---|---|---|
| `t_user` | auth/user | 用户基本信息 + 密码 | 读写分离 |
| `t_user_account` | user | 资金账户（可用/冻结/总资产） | FOR UPDATE + 乐观锁 |
| `t_user_login_log` | auth | 登录日志 | 异步写入 |
| `t_market_stock_info` | market | 股票基础信息 | Caffeine 缓存 |
| `t_trade_order` | trade | 委托订单 | 乐观锁 + 幂等键 |
| `t_trade_order_archive` | trade | 历史委托归档 | 批处理迁移 + 合并查询 |
| `t_trade_deal` | trade | 成交记录 | 按月分区 |
| `t_trade_fee_config` | trade | 手续费配置 | Caffeine 缓存 |
| `t_portfolio_position` | portfolio | 持仓明细 | FOR UPDATE + 乐观锁 |
| `t_portfolio_fund_flow` | portfolio | 资金流水 | 按月分区 |
| `t_portfolio_asset_snapshot` | portfolio | 每日资产快照 | 按月分区 |
| `t_watchlist_item` | watchlist | 自选股 | 读写分离 |
| `t_notification_message` | notification | 站内消息 | 异步写入 |
| `t_sys_config` | admin | 系统配置 | Caffeine 缓存 |

### 8.2 分区策略

```sql
-- 资金流水按月分区
CREATE TABLE t_portfolio_fund_flow (
    ...
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
) PARTITION BY RANGE (created_at);

CREATE TABLE t_portfolio_fund_flow_2026_01 PARTITION OF t_portfolio_fund_flow
    FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');
-- 每月自动创建分区（pg_partman 或定时DDL）

-- 资产快照按月分区
CREATE TABLE t_portfolio_asset_snapshot (...) PARTITION BY RANGE (snapshot_date);

-- 成交记录按月分区
CREATE TABLE t_trade_deal (...) PARTITION BY RANGE (traded_at);
```

### 8.3 关键关系

```
User 1──1 Account
User 1──N Order
User 1──N Position
User 1──N WatchlistItem
Order 1──N Trade(成交)
Position N──1 StockInfo
User 1──N FundFlow
User 1──N AssetSnapshot
```

### 8.4 连接池配置

| 参数 | 主库 | 从库 | 说明 |
|---|---|---|---|
| `maximumPoolSize` | 30 | 50 | 从库承担更多读请求 |
| `minimumIdle` | 10 | 20 | 预热连接数 |
| `connectionTimeout` | 3000ms | 3000ms | 获取连接超时 |
| `idleTimeout` | 600000ms | 600000ms | 空闲回收 |
| `maxLifetime` | 1800000ms | 1800000ms | 最大生命周期 |
| `leakDetectionThreshold` | 30000ms | 30000ms | 泄漏检测 |

---

## 九、技术选型理由

| 选型 | 理由 |
|---|---|
| **PostgreSQL** vs MySQL | PG 对 NUMERIC 精度、窗口函数、JSONB、部分索引、声明式分区支持更好，金融系统优选 |
| **Redis Cluster** | 百万用户场景需分布式缓存，Cluster 模式支持水平扩展、Pub/Sub 跨节点广播 |
| **Caffeine** + Redis | 两级缓存：L1 JVM 本地亚微秒延迟（行情热数据），L2 Redis 分布式共享 |
| **RabbitMQ** vs Kafka | 模拟交易消息量中等，RabbitMQ 延迟更低、ACK语义更清晰、DLQ 机制完善 |
| **ECharts** vs TradingView | ECharts 免费开源、中文文档好、K线能力足够，TradingView 需商业授权 |
| **WebSocket(STOMP)** vs SSE | 双向通信、Spring原生支持、可按topic订阅、生态成熟 |
| **JWT** vs Session | 前后端分离架构下 JWT 无状态更合适，结合 Redis 黑名单兼顾登出需求 |
| **MyBatis-Plus** vs JPA | 对SQL控制更精细，金融级查询需要精确控制SQL |
| **Nginx** 负载均衡 | 成熟稳定、支持 upstream 健康检查、limit_req 限流、ip_hash 粘性会话 |
| **k6** vs JMeter | 脚本化(JS)更现代、CI友好、轻量、支持 WebSocket 压测 |
| **Playwright** vs Cypress | 多浏览器支持、更快、微软维护活跃 |

---

## 十、安全架构

```
请求 → Nginx(WAF+限流) → Rate Limiter (Redis+Lua) → JWT Filter → Controller
                                                        │
                                                  Redis 黑名单 (登出Token)
```

- 所有接口默认需要鉴权（白名单：注册/登录/刷新/行情公开接口）
- 限流分三级：全局 100/min、交易 10/min、行情 60/min
- Nginx 层全局限流：单IP 100 req/s（防DDoS粗粒度过滤）
- CORS 白名单：仅允许前端域名
- 本地开发环境使用 HTTP（如需 HTTPS 可后续按需启用）
- 敏感操作日志审计

---

## 十一、可观测性

| 层次 | 工具 | 说明 |
|---|---|---|
| 日志 | Logback JSON → Loki | 结构化日志，含traceId、userId |
| 指标 | Micrometer → Prometheus | JVM/HTTP/业务/缓存命中率 |
| 可视化 | Grafana | Dashboard + 告警 |
| 链路追踪 | Micrometer Tracing → Tempo | 全链路 traceId 贯穿 |

### 关键业务指标（Prometheus）

| 指标名 | 类型 | 说明 |
|---|---|---|
| `trade_order_created_total` | Counter | 委托创建总数 |
| `trade_order_filled_total` | Counter | 成交总数 |
| `trade_match_duration_seconds` | Histogram | 撮合耗时分布 |
| `market_quote_cache_hit_total` | Counter | 行情缓存命中(L1/L2) |
| `market_quote_cache_miss_total` | Counter | 行情缓存穿透 |
| `ws_active_connections` | Gauge | 当前活跃WS连接数 |
| `ws_push_duration_seconds` | Histogram | WS推送耗时 |
| `db_pool_active_connections` | Gauge | 数据库活跃连接 |
| `db_pool_pending_threads` | Gauge | 等待连接的线程 |
| `mq_queue_depth` | Gauge | MQ队列深度 |
| `caffeine_hit_rate` | Gauge | L1缓存命中率 |
| `redis_hit_rate` | Gauge | L2缓存命中率 |

### 告警规则

| # | 条件 | 级别 | 动作 |
|---|---|---|---|
| 1 | HTTP 5xx rate > 1% for 5min | Critical | PagerDuty |
| 2 | API P99 > 500ms for 5min | Warning | Slack |
| 3 | JVM heap > 80% for 10min | Warning | Slack |
| 4 | DB connection pool exhausted | Critical | PagerDuty |
| 5 | MQ queue depth > 1000 | Warning | Slack |
| 6 | 撮合延迟 P99 > 1s | Warning | Slack |
| 7 | L1 缓存命中率 < 60% | Warning | Slack |
| 8 | WS 连接数 > 8000/instance | Warning | 扩容提醒 |
| 9 | PG 复制延迟 > 1s | Warning | Slack |
| 10 | 登录失败率 > 30% for 5min | Warning | 安全告警 |

---

## 十二、容量规划与扩展路径

### 12.1 容量估算

| 维度 | 数值 | 计算依据 |
|---|---|---|
| 注册用户 | 100 万 | 目标规模 |
| DAU | 10 万 | 10% 日活率 |
| 峰值在线 | 5 万 | 50% 峰值集中度 |
| 峰值 WS 连接 | 5 万 | 在线用户均订阅行情 |
| 峰值下单 TPS | 3000 | 5万用户 × 6% 下单率/min / 60s |
| 行情推送 QPS | ~16700 | 5万连接 / 3s 推送间隔 |
| 日订单量 | 50 万 | DAU × 5单/天 |
| 日成交量 | 40 万 | 80% 成交率 |

### 12.2 水平扩展路径

| 阶段 | 用户规模 | 部署方式 | 实例数 |
|---|---|---|---|
| MVP | < 1 万 | 单机 (2C4G) | 1 App + 1 PG + 1 Redis |
| 成长期 | 1~10 万 | 双机 + 读写分离 | 2 App + 1 PG主 + 1 PG从 + 1 Redis |
| 规模期 | 10~100 万 | 多实例 + Redis Cluster | 4+ App + PG主从 + Redis 3主3从 + MQ集群 |
| 超大规模 | 100 万+ | 微服务拆分 | 按模块独立部署 + K8s HPA |

---

## 十三、性能压测标准

### 13.1 k6 压测场景

| 场景 | 并发 | 持续 | 通过标准 |
|---|---|---|---|
| 行情查询 | 500 VU | 5min | P95 < 50ms, P99 < 100ms, 错误率 < 0.1% |
| 下单接口 | 200 VU | 5min | P95 < 100ms, P99 < 200ms, 错误率 < 0.1% |
| 混合场景 | 1000 VU | 10min | P99 < 300ms, 错误率 < 0.5% |
| WS 连接 | 10000 连接 | 5min | 推送延迟 P99 < 500ms |
| 登录 | 100 VU | 3min | P99 < 300ms |

### 13.2 CI 集成

- 每次 PR 合入 develop 运行轻量压测（50 VU / 1min）
- 每周定时运行完整压测（上表标准）
- 压测结果写入 Grafana 长期趋势面板

---

## 十四、推荐模块优先级

| 优先级 | 模块 | 价值 | 实现成本 |
|---|---|---|---|
| P0 | Auth + User + Account | 一切功能的基础 | 中 |
| P0 | Market（行情接入 + 多级缓存） | 核心体验 + 高并发热点 | 高 |
| P0 | Trade（下单/撮合 + 并发安全） | 核心功能 + 一致性保障 | 高 |
| P0 | Portfolio（持仓/资产） | 核心功能 | 中 |
| P0 | 多级缓存 + 读写分离基础设施 | 性能基石 | 高 |
| P1 | Watchlist（自选股） | 高频使用 | 低 |
| P1 | Notification（推送） | 体验提升 | 中 |
| P1 | WebSocket 推送优化（背压/扇出）| 性能关键 | 中 |
| P2 | 排行榜 | 社交激励 | 低 |
| P2 | 行情提醒（价格触发） | 实用功能 | 中 |
| P2 | k6 压测脚本 + CI集成 | 质量保障 | 中 |
| P3 | Admin（管理后台） | 运营需要 | 中 |
| P3 | 策略回测 | 进阶功能 | 高 |
| P3 | 新闻/公告 | 内容补充 | 低 |
