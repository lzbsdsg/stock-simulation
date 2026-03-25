# 文档 D：开发路线图

> 版本：2.0 | 日期：2026-02-13 | 状态：初稿
> 目标：一人全栈，20 周完成 MVP → Beta 发布（含百万用户高并发架构）
> 变更：v2.0 整合多级缓存、读写分离、行情扇出、WS背压、分布式基础设施

---

## 一、整体时间线

```
Week  1  ████ 项目骨架 + 开发环境 + 基础设施(Redis Cluster/PG主从/Nginx)
Week  2  ████ 通用基础设施 — 多级缓存抽象 + 限流 + 读写分离路由
Week  3  ████ 认证模块 (Auth) — OTP/登录/JWT + Caffeine锁定缓存
Week  4  ████ 用户与账户模块 (User/Account) — 行级锁 + 乐观锁 + 头像上传
Week  5  ████ 行情模块 (Market) — Provider 抽象 + 多级缓存(L1+L2)
Week  6  ████ 行情模块 (Market) — Pub/Sub扇出 + 分布式锁选主 + 断路器降级
Week  7  ████ 行情模块 (Market) — WebSocket推送 + 背压控制 + 连接管理
Week  8  ████ 交易模块 (Trade) — 下单/撤单 + 幂等 + 短事务
Week  9  ████ 交易模块 (Trade) — MQ异步撮合 + 乐观锁重试 + 成交通知
Week 10  ████ 持仓与资产模块 (Portfolio) — 分区表 + 分批快照 + 读写分离查询
Week 11  ████ 前端 MVP — 认证 + 行情页面 + WS实时推送
Week 12  ████ 前端 MVP — 交易 + 持仓页面 + 背压降级展示
Week 13  ████ WebSocket完善 + 自选股 + 消息通知
Week 14  ████ 部署 — Docker集群 + Nginx LB + PG主从 + Redis Cluster
Week 15  ████ 监控 — Prometheus + Grafana + Loki + Tempo + 告警规则
Week 16  ████ CI/CD — GitHub Actions + 轻量压测门禁 + 镜像安全扫描
Week 17  ████ 性能调优 — 慢SQL + 缓存命中率 + 连接池 + JVM
Week 18  ████ k6 压测 + 安全加固
Week 19  ████ 细节打磨 + Bug 修复
Week 20  ████ Beta 发布 + 文档收尾
```

---

## 二、迭代详细计划

### Iteration 0 — Week 1：项目骨架 + 开发环境 + 基础设施

**目标**：从零搭建可运行的空项目 + 本地高并发基础设施一键启动

**基础设施任务**：
- [ ] 编写 docker-compose.dev.yml:
  - PostgreSQL 16 主库 (port 5432)
  - PostgreSQL 16 从库 (port 5433, streaming replication)
  - Redis Cluster (3主3从, ports 7000-7005) 或 Redis 单机(开发简化)
  - RabbitMQ 3.13 (带 Management UI)
  - Nginx (反向代理, port 80 → app:8080)
- [ ] 编写 nginx/nginx.conf:
  - `limit_req_zone $binary_remote_addr zone=global:10m rate=100r/s`
  - `upstream app { server app-1:8080; server app-2:8080; }` (开发阶段单实例)
  - WebSocket 代理 `/ws/` + `ip_hash` 粘性
  - 静态资源 gzip

**后端任务**：
- [ ] Spring Boot 3.2 项目初始化（Maven）
- [ ] 配置 application.yml / application-dev.yml / application-test.yml / application-prod.yml
- [ ] 集成 Lombok + MapStruct + Spotless
- [ ] 配置 MyBatis-Plus（分页插件 + 乐观锁插件）
- [ ] 配置 Spring Security 6 骨架（暂时 permitAll）
- [ ] 配置 SpringDoc OpenAPI 3（Swagger UI）
- [ ] 配置 Flyway（空迁移目录）
- [ ] 配置 Redis Cluster（Lettuce, RedisTemplate）
- [ ] 配置 RabbitMQ（ConnectionFactory, RabbitTemplate）
- [ ] 实现 `common/` 包：Result, PageResult, ErrorCode, BizException, GlobalExceptionHandler
- [ ] 配置 logback-spring.xml（JSON 结构化日志 + traceId MDC + userId MDC）
- [ ] 配置 Micrometer + Prometheus Actuator 端点
- [ ] 编写 Dockerfile（多阶段构建）

**前端任务**：
- [ ] `pnpm create vite stock-simulation-web -- --template vue-ts`
- [ ] 配置 ESLint + Prettier
- [ ] 安装 vue-router + pinia + axios + element-plus/ant-design-vue
- [ ] 配置 vite.config.ts（alias, proxy /api → Nginx:80）
- [ ] 创建目录结构（api/ components/ pages/ stores/ types/ utils/ composables/）
- [ ] 实现 Axios 请求封装（request.ts, 含 X-RateLimit-* 头解析）

**验收标准**：
- `docker-compose -f docker-compose.dev.yml up` 一键启动 PG主+从/Redis/RabbitMQ/Nginx
- `mvn spring-boot:run -Dspring.profiles.active=dev` 后端启动成功
- 访问 `http://localhost/swagger-ui/index.html`（经 Nginx 代理）
- `pnpm dev` 前端启动成功
- Actuator `/actuator/prometheus` 可访问

**产出物**：
- 项目骨架代码
- docker-compose.dev.yml (含全部基础设施)
- nginx/nginx.conf
- Dockerfile

---

### Iteration 1 — Week 2：通用基础设施 — 多级缓存 + 限流 + 读写分离

**目标**：实现可复用的高并发基础组件，后续模块直接使用

**后端任务**：
- [ ] **多级缓存抽象**:
  - `MultiLevelCache.java` — 泛型缓存接口 (get/put/evict)
  - `MultiLevelCacheManager.java` — L1 Caffeine + L2 Redis 编排
  - `CacheInvalidateListener.java` — Redis Pub/Sub `cache:invalidate:{region}` 监听 → 清除本地 L1
  - `CaffeineConfig.java` — 多 region 配置 (quote TTL=3s/stock TTL=5min/config TTL=10min)
  - `RedisConfig.java` — Cluster + Pub/Sub + RedisTemplate (JSON序列化)
- [ ] **限流组件**:
  - `RateLimit.java` — 自定义注解 `@RateLimit(limit=10, window=60, key="trade")`
  - `RateLimitAspect.java` — AOP，Redis + Lua 滑动窗口/令牌桶
  - Lua 脚本: `rate_limit.lua`
  - 响应头注入: `X-RateLimit-Limit` / `X-RateLimit-Remaining` / `X-RateLimit-Reset`
- [ ] **读写分离路由**:
  - `ReadOnly.java` — `@ReadOnly` 方法注解
  - `DataSourceRoutingConfig.java` — `AbstractRoutingDataSource` 主从路由
  - `DataSourceContextHolder.java` — ThreadLocal 存储当前数据源标记
  - `DataSourceAspect.java` — AOP 拦截 `@ReadOnly` / `@Transactional`
  - HikariCP 主库 (min=10, max=30) + 从库 (min=20, max=50)
- [ ] **异步配置**:
  - `AsyncConfig.java` — 自定义线程池 (core=8, max=32, queue=500)
- [ ] **TraceId 工具**:
  - `TraceIdUtil.java` + MDC Filter

**测试**：
- [ ] MultiLevelCacheManagerTest (5条: L1命中/L2命中/回源/失效同步/TTL过期)
- [ ] RateLimitAspectTest (3条: 正常/超限/窗口重置)
- [ ] DataSourceRoutingTest (3条: 默认主库/ReadOnly从库/事务强制主库)

**验收标准**：
- `@RateLimit(limit=10, window=60)` 注解生效，超限返回 429 + 限流头
- 多级缓存 get: L1 → L2 → lambda回源，L2命中时自动回填L1
- Redis Pub/Sub 发消息后所有实例 L1 被清除
- `@ReadOnly` 方法路由到从库，`@Transactional` 方法走主库
- 响应头包含 `X-Cache-Status: HIT-L1 / HIT-L2 / MISS`

**产出物**：
- 可复用的 common/cache/, common/annotation/, common/aspect/ 包
- config/ 下 Caffeine/Redis/DataSource/Async 配置类

---

### Iteration 2 — Week 3：认证模块 (Auth)

**目标**：邮箱OTP注册 + 密码登录 + JWT签发/刷新/登出 + 锁定缓存

**Flyway 迁移**：
- [ ] `V20260213_001__create_user_tables.sql` → t_user, t_user_login_log

**后端任务**：
- [ ] **Domain**: User entity, OtpDomainService, PasswordDomainService
- [ ] **Repository**: UserRepository (interface) → UserRepositoryImpl (MyBatis-Plus)
- [ ] **Infrastructure**: OtpRedisGateway (Redis TTL=5min, 验证后删除)
- [ ] **Infrastructure**: JwtTokenProvider (access 30min + refresh 7d + Rotation)
- [ ] **Infrastructure**: EmailGateway (SMTP → RabbitMQ 异步发送)
- [ ] **Application**: AuthApplicationService（编排OTP/注册/登录/刷新/登出）
- [ ] **Controller**: AuthController（8个端点, 含忘记密码/重置密码）
- [ ] **Security**: SecurityConfig（/auth/** permitAll，其他 authenticated）
- [ ] **Security**: JwtAuthenticationFilter（OncePerRequestFilter + Redis黑名单检查）
- [ ] **限流**: OTP 发送 — `@RateLimit` + Redis SETNX `otp:limit:{email}` TTL=60s + IP 限制 20/h
- [ ] **缓存**: 登录锁定状态写 Caffeine + Redis 双写，登录时先查 Caffeine(<1ms快速拒绝)
- [ ] **MQ**: 邮件发送 Producer → email.send Exchange

**测试**：
- [ ] OtpDomainServiceTest (4条)
- [ ] PasswordDomainServiceTest (6条)
- [ ] AuthApplicationServiceTest (9条)
- [ ] AuthControllerApiTest (13条: 含忘记密码/重置密码)
- [ ] OtpRedisGatewayIntegrationTest (3条)

**验收标准**：
- POST /api/v1/auth/otp/send → 邮箱收到6位验证码（MQ异步发送）
- POST /api/v1/auth/register → 返回 JWT
- POST /api/v1/auth/login → 返回 accessToken + refreshToken
- POST /api/v1/auth/refresh → 返回新 Token，旧 Refresh 失效
- POST /api/v1/auth/logout → Token 加入 Redis 黑名单
- 受保护接口无 Token → 401
- 密码错误5次 → 锁定30分钟 (Caffeine缓存<1ms检查)
- OTP 60s 内重复发送 → 429 + X-RateLimit-* 头

---

### Iteration 3 — Week 4：用户与账户模块 (User/Account)

**目标**：用户资金账户创建、冻结/解冻机制（行级锁 + 乐观锁）+ 用户头像上传与更新

**状态**：✅ 已完成（实现与验收口径见 `iteration-3-delivery.md` 与 `iteration-3-acceptance-script.md`）

**Flyway 迁移**：
- [x] `V20260213_002__create_account_table.sql` → t_user_account (含 version 字段)

**后端任务**：
- [x] **Domain**: Account entity (余额恒等式: total = available + frozen), AccountDomainService
- [x] **Domain**: AccountDomainService.freeze() / unfreeze() — 纯业务规则校验
- [x] **Repository**: AccountRepository → AccountRepositoryImpl
- [x] **Application**: AccountApplicationService（账户初始化 + 冻结/解冻对外接口）
- [x] **Controller**: UserController（查看/修改用户信息 + 头像上传）
- [x] **Storage**: AvatarStorageService（本地存储目录 `uploads/avatars/{yyyyMM}/`）
- [x] **Config**: StaticResourceConfig（`/uploads/**` 静态资源映射）
- [x] **Security**: 放行头像静态访问路径 `/uploads/**`
- [x] 注册流程集成：注册时自动创建 Account（初始资金）
- [x] **行级锁**: `SELECT ... FOR UPDATE` 仅锁当前用户 Account 行
- [x] **乐观锁**: `UPDATE ... WHERE version = ?`，MyBatis-Plus `@Version` 注解

**测试**：
- [x] AccountDomainServiceTest (8条: 冻结/解冻/余额不足/恒等式校验)
- [x] AccountRepositoryIntegrationTest (4条: CRUD + 乐观锁冲突)
- [x] UserControllerApiTest 增加头像上传 `multipart/form-data` 用例
- [x] **TradeConcurrencyTest** (3条: 并发冻结同一账户 → 行级锁串行 / 不同账户 → 完全并行 / 乐观锁冲突重试)

**验收标准**：
- 注册成功后 t_user_account 有对应记录
- 可用余额冻结/解冻正确，balance_available + balance_frozen = total 恒等
- 乐观锁冲突时抛出 OptimisticLockingFailureException
- `SELECT ... FOR UPDATE` 正确锁定行，并发冻结不超卖
- 10线程并发冻结同一账户 → 仅一个成功（余额不足场景）
- POST `/api/v1/user/avatar` 上传图片成功并返回 `avatarUrl`
- 返回的 `avatarUrl` 可通过 `GET /uploads/**` 直接访问
- 头像格式限制生效（jpg/jpeg/png/webp/gif），文件大小限制生效（默认 2MB）

---

### Iteration 4 — Week 5：行情模块（Provider抽象 + 多级缓存）

**目标**：接入外部行情数据源 + 多级缓存(L1 Caffeine + L2 Redis)

**Flyway 迁移**：
- [x] `V20260213_003__create_stock_info_table.sql` → t_market_stock_info（建表）
- [x] `V20260324_004__seed_market_stock_info.sql` → 首批样例种子（幂等 upsert）
- [x] `V20260324_005__seed_market_stock_info_from_csv.sql` → CSV 基础导入（约 2800 条，幂等 upsert）
- [x] `V20260324_006__seed_market_stock_info_full_a_share.sql` → 全量 A 股导入（当前快照 5490 条，含沪深北，幂等 upsert）

**后端任务**：
- [ ] **Domain**: StockInfo entity, QuoteSnapshot value object, KLinePoint, KLinePeriod
- [ ] **Infrastructure / Provider**: MarketDataProvider 接口定义
- [ ] **Infrastructure / Provider**: SinaMarketDataAdapter（解析新浪 HTTP 响应）
- [ ] **Infrastructure / Provider**: TencentMarketDataAdapter（解析腾讯 HTTP 响应）
- [ ] **Infrastructure / Provider**: MockMarketDataAdapter（随机波动，开发/测试用）
- [ ] **Domain / Service**: MarketDataFacade — 多级缓存编排:
  ```
  L1 Caffeine(TTL=3s, max=5000) → L2 Redis(TTL=5s+random) → Provider回源
  L2命中 → 回填L1；回源成功 → 写L1+L2
  ```
- [ ] **Infrastructure / Gateway**: MarketCacheGateway（Caffeine + Redis 读写封装）
- [ ] **防击穿**: 同一 key 分布式锁回源（Redis SETNX `market:load:{code}` TTL=3s）
- [ ] **防雪崩**: L2 TTL = 5s + random(0,500ms)
- [ ] **防穿透**: 不存在的 stockCode 空值缓存 TTL=30s
- [ ] **Repository**: StockInfoRepository → StockInfoRepositoryImpl
- [ ] **Stock 搜索**: 股票列表 Caffeine TTL=5min 全量缓存，内存搜索 (<1ms)
- [ ] **Controller**: MarketController（quote/quotes/kline/search）
- [ ] 响应头注入 `X-Cache-Status: HIT-L1 / HIT-L2 / MISS / STALE`

**测试**：
- [ ] SinaMarketDataAdapterTest（Mock HTTP 响应解析）
- [ ] MockMarketDataAdapterTest
- [ ] MarketDataFacadeTest (7条: L1命中/L2命中/回源/防击穿锁/防穿透空值/stale降级/全部失败)
- [ ] MarketCacheGatewayIntegrationTest (3条)

**验收标准**：
- `provider.getQuote("sh600519")` 返回正确行情
- 首次请求穿透到 Provider，后续 3s 内 L1 命中（`X-Cache-Status: HIT-L1`）
- 3s~5s 内 L2 命中（`X-Cache-Status: HIT-L2`）
- 并发 100 请求同一股票 → 仅 1 次回源（分布式锁）
- 不存在的代码 → 空值缓存 30s
- Stock 搜索: GET /api/v1/market/search?keyword=茅台 → <1ms

---

### Iteration 5 — Week 6：行情模块（Pub/Sub扇出 + 降级）

**目标**：行情拉取主节点选举 + Pub/Sub 多实例扇出 + Provider 断路器降级

**后端任务**：
- [ ] **Infrastructure / Ingest**: MarketIngestService — 行情拉取主节点:
  - Redis 分布式锁 `market:ingest:leader` TTL=10s + 续期
  - 仅持锁实例定时拉取行情（3s/次）
  - 拉取结果写 Redis L2 + 发布 Pub/Sub `market:quote:broadcast`
- [ ] **Infrastructure / Ingest**: MarketPubSubListener — Pub/Sub 订阅:
  - 所有 App 实例订阅 `market:quote:broadcast`
  - 收到消息 → 更新本地 L1 Caffeine
- [ ] **降级 — 断路器状态机**:
  ```
  CLOSED(正常) →连续失败3次→ OPEN(断路30s) →30s后→ HALF_OPEN(探测1次)
                                     ↑                       │
                                     └── 失败 ──────── OPEN
                                         成功 → CLOSED
  ```
  - 主 Provider 失败 → 备 Provider → stale 缓存 → 错误码
  - 记录降级指标 (`market.provider.fallback.total` Counter)
- [ ] **批量 API**: batchGetQuotes 优化 — 逐只检查 L1/L2，仅 MISS 的回源，合并返回

**测试**：
- [ ] MarketIngestServiceTest (3条: 选主/续期/故障转移)
- [ ] MarketPubSubListenerTest (2条: 收到广播更新L1/序列化异常忽略)
- [ ] CircuitBreakerTest (4条: CLOSED/OPEN/HALF_OPEN/恢复)
- [ ] batchGetQuotesTest (2条: 部分命中/全部命中)

**验收标准**：
- 启动 2 个 App 实例 → 仅 1 个拉取行情
- 拉取后另一实例 L1 在 <100ms 内更新
- 主 Provider 失败3次 → 自动切备 Provider → 30s 后探测恢复
- 全部 Provider 失败 → 返回 stale 缓存 + `X-Cache-Status: STALE`

---

### Iteration 6 — Week 7：行情模块（WebSocket + 背压）

**目标**：WebSocket 实时推送 + 背压控制 + 连接注册表

**后端任务**：
- [ ] **Config**: WebSocketConfig — STOMP over SockJS, endpoint `/ws/market`
- [ ] **Infrastructure / WebSocket**: MarketWebSocketHandler:
  - 连接注册表 `ConcurrentHashMap<userId, WsSession>`
  - 握手时 JWT 鉴权
  - 连接数计数 + Gauge 指标 `ws_active_connections`
  - 连接上限: 10000/实例，超限返回 503
- [ ] **Infrastructure / WebSocket**: MarketPushScheduler:
  - 监听 Pub/Sub → 拿到行情数据 → 按用户自选股过滤 → 推送
  - 推送频率: 3s/次
- [ ] **背压控制**:
  - 推送队列深度 > 100 → 丢弃最旧消息
  - 推送延迟 > 5s → 降级为 10s 推送周期
  - 发送缓冲区 > 64KB → 暂停推送
  - 连接总数 > 8000 → 告警 (`ws.connection.high` Alert)
- [ ] **指标**:
  - `ws_active_connections` Gauge
  - `ws_push_duration_seconds` Histogram
  - `ws_push_dropped_total` Counter (背压丢弃)

**测试**：
- [ ] MarketWebSocketHandlerTest (4条: 连接/断开/鉴权失败/超限拒绝)
- [ ] BackpressureTest (3条: 队列满丢弃/延迟降级/缓冲区暂停)

**验收标准**：
- WebSocket 订阅后每 3s 收到行情推送
- 连接注册表正确维护（断开自动清理）
- 模拟 >10000 连接 → 新连接返回 503
- 模拟慢消费者 → 背压降级为 10s 推送
- Prometheus 可采集 ws_active_connections

---

### Iteration 7 — Week 8：交易模块（下单/撤单）

**目标**：委托下单（买入/卖出）+ 撤单 + 幂等 + 短事务（不含撮合）

**Flyway 迁移**：
- [ ] `V20260213_004__create_trade_tables.sql` → t_trade_order (含 client_order_id UNIQUE INDEX, version), t_trade_deal, t_trade_fee_config
- [ ] `V20260213_005__create_portfolio_tables.sql` → t_portfolio_position (含 version), t_portfolio_fund_flow (按月分区)

**后端任务**：
- [ ] **Domain**: Order entity, Trade entity, OrderSide/OrderStatus/OrderType value objects
- [ ] **Domain**: OrderDomainService — 前置校验（全部走 Caffeine 缓存，不访问 DB）:
  - 交易时间 ✓ (Caffeine `config` region)
  - 涨跌停 ✓ (Caffeine `quote` region)
  - 100股整数倍 ✓ (纯计算)
- [ ] **Domain**: FeeCalculator（佣金万三最低5元 + 印花税千一仅卖 + 过户费十万分一）
- [ ] **Infrastructure**: IdempotencyGateway（Redis SETNX `idempotent:order:{clientOrderId}` TTL=5min）
- [ ] **Repository**: OrderRepository, TradeRepository → 实现
- [ ] **Application**: TradeApplicationService.placeOrder — 短事务(<50ms):
  ```
  1. 幂等检查 (Redis)
  2. 前置校验 (Caffeine)
  3. BEGIN TRANSACTION:
     a. SELECT ... FOR UPDATE 锁 Account 行
     b. 校验余额
     c. 冻结资金
     d. INSERT Order(PENDING)
     e. INSERT FundFlow(FREEZE)
  4. COMMIT
  5. 异步发 MQ 撮合消息
  ```
- [ ] **Application**: TradeApplicationService.cancelOrder — CAS 乐观更新:
  ```
  1. UPDATE Order SET status=CANCELLED WHERE status=PENDING AND version=?
  2. 成功 → 解冻资金/持仓 (事务内)
  3. 失败 → 返回撤单失败
  ```
- [ ] **Controller**: OrderController (placeOrder/cancelOrder/listOrders/listTrades)
- [ ] **限流**: `@RateLimit(limit=10, window=60)` 下单/撤单接口
- [ ] **MQ**: OrderMessageProducer — 下单后发消息到 `trade.match` Exchange

**测试**：
- [ ] OrderDomainServiceTest (9条: 含缓存校验路径验证)
- [ ] FeeCalculatorTest (7条)
- [ ] TradeApplicationServiceTest — 下单/撤单 (8条: 含幂等/余额不足/并发冻结)
- [ ] OrderControllerApiTest (12条: 含限流429/幂等409)
- [ ] OrderRepositoryIntegrationTest (4条)

**验收标准**：
- POST /api/v1/trade/orders → 创建 PENDING 订单，资金被冻结，MQ 消息已发送
- DELETE /api/v1/trade/orders/{id} → 撤单成功，资金解冻
- 重复 clientOrderId → 409 幂等拦截
- 非交易时间下单 → 400（校验走 Caffeine，<1ms）
- 非100股整数倍 → 400
- 下单超 10/min → 429 + X-RateLimit-* 头
- 事务执行时间 < 50ms (日志打印事务耗时)

---

### Iteration 8 — Week 9：交易模块（MQ异步撮合 + 成交）

**目标**：MQ 消费者异步撮合 + 乐观锁重试 + 成交通知 + 收盘结算

**后端任务**：
- [ ] **Domain**: MatchEngine — 撮合判断:
  - 买入: order.price >= currentPrice → 按 currentPrice 成交
  - 卖出: order.price <= currentPrice → 按 currentPrice 成交
  - 行情获取走多级缓存 (L1 → L2 → Provider)
- [ ] **Infrastructure / MQ**: MatchConsumer — MQ Consumer:
  - `prefetch = 10`, `concurrentConsumers = 8`
  - 幂等检查: orderId 是否已撮合
  - 撮合事务 (单事务):
    ```
    a. 计算实际手续费
    b. INSERT Trade
    c. UPDATE Order(FILLED) — 乐观锁
    d. UPDATE Position(加减持仓,重算成本价) — 乐观锁
    e. UPDATE Account(解冻+扣费/入账) — 乐观锁
    f. INSERT FundFlow(TRADE)
    ```
  - 乐观锁冲突 → 重试 (max 3次, 50ms × 2^n 退避)
  - 重试 3 次仍失败 → 进入 DLQ（死信队列）
- [ ] **MQ**: 成交后发送事件 `trade.filled` (fanout Exchange):
  - → NotificationConsumer (写站内信 + WS推送)
  - → 未来可接: 异步对账/排行榜更新
- [ ] **RabbitMQ Config**: 配置 DLQ (dead-letter-exchange + dead-letter-routing-key)
- [ ] **Domain**: PositionDomainService:
  - 加权平均成本价计算
  - T+1 冻结: `frozen_until = 下一交易日 15:00`
- [ ] **Scheduler**: 收盘结算 (15:00, @Scheduled + 分布式锁防重):
  - 分批过期 PENDING 订单 (每批200)
  - 分批更新今日买入持仓 frozen_until
- [ ] **Archive**: 历史委托归档 (03:30, @Scheduled + 分布式锁防重):
  - 归档范围：`CANCELLED/EXPIRED/REJECTED` 且无成交明细的订单
  - 保留策略：主表保留近 N 天（默认 7 天），超期批量迁移至 `t_trade_order_archive`
  - 查询兼容：历史委托查询需合并主表 + 归档表，避免功能回退

**测试**：
- [ ] MatchEngineTest (6条)
- [ ] MatchConsumerTest (5条: 成交/不满足条件/幂等/乐观锁重试/DLQ)
- [ ] TradeApplicationServiceTest — 撮合部分 (6条)
- [ ] TradeFullFlowIntegrationTest (3条: 买入全链路/卖出全链路/撤单全链路)
- [ ] **TradeConcurrencyTest** (3条: 并发撮合同一订单 → 幂等 / 并发撮合同一用户不同订单 → 乐观锁重试成功)

**验收标准**：
- 下单后 MQ 消费 → 撮合 → 订单 FILLED → 持仓更新 → 资金结算
- 买入后持仓标记 T+1 冻结
- 卖出时 T+1 内拒绝
- 乐观锁冲突重试成功（日志可见重试次数）
- 10 线程并发撮合同一用户 → 全部成功（乐观锁重试）
- 收盘时 PENDING 订单自动过期
- 归档任务执行后，历史委托在归档表可查，`GET /api/v1/trade/orders?scope=history` 结果不丢失

---

### Iteration 9 — Week 10：持仓与资产模块 (Portfolio)

**目标**：持仓查询、资金流水(分区表)、资产快照(分批处理)、收益曲线

**Flyway 迁移**：
- [ ] `V20260213_006__create_portfolio_snapshot_tables.sql` → t_portfolio_asset_snapshot (按月分区)
- [ ] `V20260214_001__add_table_partitions.sql` → 创建 fund_flow/asset_snapshot/trade_deal 初始分区

**后端任务**：
- [ ] **Domain**: Position, FundFlow, AssetSnapshot entities
- [ ] **Domain**: AssetSnapshotService（快照生成 + UNIQUE(user_id, snapshot_date) 幂等）
- [ ] **Repository**: PositionRepository, FundFlowRepository, AssetSnapshotRepository → 实现
- [ ] **Application**: PortfolioApplicationService:
  - overview: 聚合 Account + Position + 行情缓存(L1 Caffeine) → 实时总资产/浮动盈亏
  - positions: DB 持仓 + L1 行情合并返回
  - fund-flows / equity-curve: `@ReadOnly` → 从库查询
- [ ] **Controller**: PortfolioController（overview/positions/fund-flows/equity-curve）
- [ ] **Scheduler**: AssetSnapshotScheduler — 每日 15:15 分批快照:
  - 分布式锁 `snapshot:daily:{date}` 防重复执行
  - 每批 500 用户
  - 批量获取行情 (batchGetQuotes, L1→L2)
  - 计算总资产 = 可用 + 冻结 + 持仓市值(收盘价)
  - 收益率 = (总资产 - 初始资金) / 初始资金 × 100%
  - 批量 INSERT AssetSnapshot

**测试**：
- [ ] PositionDomainServiceTest (6条)
- [ ] AssetSnapshotServiceTest (3条: 正常快照/幂等/空持仓)
- [ ] PositionRepositoryIntegrationTest (3条)
- [ ] FundFlowPartitionTest (2条: 跨月查询/分区路由)
- [ ] PortfolioControllerApiTest (4条)

**验收标准**：
- GET /api/v1/portfolio/overview → 总资产/可用/冻结/市值（行情走 L1 缓存）
- GET /api/v1/portfolio/positions → 含当前市价与浮动盈亏
- GET /api/v1/portfolio/fund-flows → 从库查询，分区表透明
- GET /api/v1/portfolio/equity-curve → 收益曲线
- 快照任务分批执行（日志可见批次）
- 重复执行快照 → 幂等不报错

---

### Iteration 10 — Week 11：前端 MVP（认证 + 行情）

**目标**：完成认证页面 + 行情页面 + K线图 + WS实时推送

**前端任务**：
- [ ] **Layout**: AuthLayout（居中卡片）, DefaultLayout（Header+Sidebar+Content）
- [ ] **Auth Pages**: LoginPage, RegisterPage, ForgotPasswordPage
- [ ] **Auth Store**: useAuthStore（login/logout/refreshToken）
- [ ] **Axios 拦截器**: 401 自动 refresh → 重试原请求；解析 X-RateLimit-* 头
- [ ] **Router**: 路由守卫（未登录→/login）
- [ ] **Market Pages**: MarketPage（行情列表）, StockDetailPage（K线+交易面板）
- [ ] **Market Components**: StockSearch, QuoteCard, KLineChart, MarketOverview
- [ ] **Market Store**: useMarketStore（quotes + WS 连接管理）
- [ ] **useWebSocket Composable**:
  - STOMP over SockJS 连接
  - JWT 鉴权
  - 自动重连 + 指数退避 (1s→2s→4s→8s→16s→30s max)
  - 背压检测: 推送延迟 > 5s → 降级显示频率
  - 检查 `X-Cache-Status` 头判断数据新鲜度
- [ ] **ECharts**: K线图渲染 + MA均线 + 成交量 + dataZoom

**验收标准**：
- 注册→登录跳转 Dashboard
- Token 过期自动刷新，无感续期
- 行情页面实时更新（WS推送 3s/次）
- WS 断线自动重连（指数退避）
- K线图可按日K/周K/月K切换
- 限流时前端显示友好提示

---

### Iteration 11 — Week 12：前端 MVP（交易 + 持仓）

**目标**：完成交易页面 + 持仓页面 + 资产总览

**前端任务**：
- [ ] **Trade Components**: OrderForm（买入/卖出表单）, OrderList, TradeHistory
- [ ] **Trade Page**: TradePage（交易面板 + 委托列表 + 成交记录）
- [ ] **Trade Store**: useTradeStore（placeOrder/cancelOrder/orders/trades）
- [ ] **Portfolio Components**: AssetOverview, PositionTable, EquityCurve, FundFlowTable
- [ ] **Portfolio Page**: PortfolioPage（资产总览 + 持仓 + 收益曲线 + 流水）
- [ ] **Portfolio Store**: usePortfolioStore（overview/positions/fundFlows）
- [ ] **Dashboard**: DashboardPage（资产总览卡片 + 自选股简要行情）
- [ ] 交易表单校验（100股整数倍、价格范围）
- [ ] 金额格式化（千分位、颜色标记红涨绿跌）
- [ ] 幂等处理: 下单按钮防重复点击 + clientOrderId UUID
- [ ] 成交 WS 通知: 订阅 `/user/queue/trade-notify` → Toast 弹窗

**验收标准**：
- 可在 StockDetailPage 直接下单
- 委托列表实时刷新，可撤单
- 持仓页面显示浮动盈亏（实时计算）
- 收益曲线 ECharts 折线图
- Dashboard 展示资产概览
- 下单按钮防重复点击生效

---

### Iteration 12 — Week 13：WebSocket完善 + 自选股 + 消息通知

**目标**：完善实时推送、自选股管理、站内消息通知

**Flyway 迁移**：
- [ ] `V20260213_006__create_watchlist_table.sql` → t_watchlist_item
- [ ] `V20260213_007__create_notification_table.sql` → t_notification_message

**后端任务**：
- [ ] **Watchlist**: WatchlistController + WatchlistApplicationService + WatchlistRepository
- [ ] **Notification**: NotificationController + NotificationApplicationService
- [ ] **Notification MQ**: TradePushConsumer — 消费 `trade.filled` 事件 → 写站内信 + WS推送
- [ ] WebSocket 用户私有频道（`/user/queue/notification`）
- [ ] 行情推送优化：仅推送用户已订阅的自选股（过滤后推送）

**前端任务**：
- [ ] **Watchlist Page**: 自选股列表 + 添加/删除 + 拖拽排序
- [ ] **Watchlist Store**: useWatchlistStore
- [ ] **Notification**: 右上角消息铃铛 + 未读计数 + 下拉列表
- [ ] 成交通知 Toast 弹窗
- [ ] WS 订阅 `/user/queue/notification`

**验收标准**：
- 可添加/删除自选股（最多50只）
- 自选股行情实时推送（WS，3s/次）
- 下单成交后收到站内消息通知 + Toast
- 通知可标记已读

---

### Iteration 13 — Week 14：部署 — Docker集群 + Nginx LB + PG主从 + Redis Cluster

**目标**：容器化全栈高可用部署

**任务**：
- [ ] 编写生产 Dockerfile（后端多阶段 + 前端 Nginx）
- [ ] 编写 docker-compose.yml（高并发架构版）:
  ```yaml
  services:
    nginx:          # 反向代理+负载均衡+限流+ip_hash
    app-1:          # Spring Boot 实例 1
    app-2:          # Spring Boot 实例 2
    pg-master:      # PostgreSQL 主库
    pg-slave-1:     # PostgreSQL 从库 1
    pg-slave-2:     # PostgreSQL 从库 2 (可选)
    redis-node-1~6: # Redis Cluster 3主3从
    rabbitmq:       # RabbitMQ
    prometheus:     # 监控采集
    grafana:        # 可视化
    loki:           # 日志聚合
  ```
- [ ] 编写 nginx/nginx.conf（生产版）:
  - limit_req_zone 全局限流
  - upstream app 负载均衡 + 健康检查
  - WebSocket ip_hash 粘性会话
  - 静态资源 gzip + cache-control
  - SSL 终止（Let's Encrypt）
- [ ] PG 主从配置:
  - streaming replication 配置脚本
  - recovery.conf / standby.signal
  - 复制延迟监控
- [ ] Redis Cluster 初始化脚本 (`redis-cli --cluster create`)
- [ ] 配置 application-prod.yml（主从数据源、Redis Cluster 节点列表）
- [ ] 健康检查: `/actuator/health` 含 DB/Redis/MQ 状态

**验收标准**：
- `docker-compose up -d` 一键启动全部服务（2 App + PG主从 + Redis Cluster + MQ + Nginx）
- Nginx 负载均衡到 2 个 App 实例
- WebSocket 粘性会话到同一实例
- 写操作走主库，`@ReadOnly` 查询走从库
- Redis Cluster 正常工作（CLUSTER INFO: cluster_state=ok）
- 停掉 app-1 后 Nginx 自动摘除，流量全到 app-2

---

### Iteration 14 — Week 15：监控 — Prometheus + Grafana + Loki + Tempo + 告警

**目标**：构建可观测性全栈（指标+日志+链路+告警）

**任务**：
- [ ] 编写 prometheus/prometheus.yml（抓取所有 App 实例 `/actuator/prometheus`）
- [ ] 编写 prometheus/alert-rules.yml — 10+ 告警规则:
  - HTTP 5xx rate > 1% → Critical
  - API P99 > 500ms → Warning
  - JVM heap > 80% → Warning
  - DB connection pool exhausted → Critical
  - MQ queue depth > 1000 → Warning
  - 撮合延迟 P99 > 1s → Warning
  - L1 缓存命中率 < 60% → Warning
  - WS 连接数 > 8000/实例 → Warning (扩容)
  - PG 复制延迟 > 1s → Warning
  - 登录失败率 > 30% → Warning (安全)
- [ ] 导入/自定义 Grafana Dashboard:
  - JVM Dashboard (heap/GC/thread)
  - HTTP Dashboard (QPS/P95/P99/错误率)
  - Trade Dashboard (下单量/成交量/撮合延迟)
  - Market Dashboard (缓存命中率/Provider延迟/WS连接数)
  - DB Dashboard (连接池/慢查询/复制延迟)
- [ ] 配置 Loki + Promtail（收集容器日志）
- [ ] 配置 Micrometer Tracing → Tempo（全链路追踪）
- [ ] 配置自定义业务指标:
  - `trade_order_created_total` Counter
  - `trade_order_filled_total` Counter
  - `trade_match_duration_seconds` Histogram
  - `market_quote_cache_hit_total{level=L1/L2}` Counter
  - `ws_active_connections` Gauge
  - `db_pool_active_connections{source=master/slave}` Gauge

**验收标准**：
- Prometheus 可采集所有指标
- Grafana Dashboard 可视化 (5 个面板)
- 模拟 5xx → 告警触发
- 模拟 WS 高连接 → 告警触发
- 全链路 traceId 可在 Tempo 中搜索
- 日志可在 Loki 中按 userId/traceId 搜索

---

### Iteration 15 — Week 16：CI/CD — GitHub Actions + 门禁

**目标**：自动化 CI/CD 流水线 + 轻量压测门禁

**任务**：
- [ ] 编写 `.github/workflows/ci.yml`:
  - 触发: PR → develop/main
  - 步骤: 编译 → Spotless检查 → 单测 → 集成测试(Testcontainers) → JaCoCo覆盖率(≥70%) → 构建Docker镜像 → Trivy扫描(0 Critical) → 轻量k6压测(50VU/1min)
- [ ] 编写 `.github/workflows/deploy.yml`:
  - 触发: push main
  - 步骤: 构建镜像 → 推送Registry → SSH部署 → 健康检查 → 通知
- [ ] 编写 `.github/workflows/perf-weekly.yml`:
  - 触发: cron 每周日 02:00
  - 步骤: 启动测试环境 → 完整k6压测(5场景, 上表标准) → 结果写入Grafana
- [ ] 轻量压测脚本: k6/smoke-test.js (50VU, 1min, P99 < SLO×2)
- [ ] PlantUML / Mermaid CI 流程图

**验收标准**：
- PR 自动触发 CI → 编译+测试+格式+覆盖率+镜像+扫描+轻量压测 全部通过
- push main → 自动构建 + 部署 + 健康检查
- 每周压测结果可在 Grafana 趋势面板查看

---

### Iteration 16 — Week 17：性能调优

**目标**：基于监控数据，系统性调优

**任务**：
- [ ] **慢 SQL 优化**:
  - 开启 PG slow_query_log (> 50ms)
  - EXPLAIN ANALYZE 优化 TOP 10 慢 SQL
  - 补充缺失索引（联合索引最左前缀）
  - fund_flow/asset_snapshot 分区查询验证
- [ ] **缓存优化**:
  - Caffeine 命中率监控 → 调整 maxSize/TTL
  - Redis 命中率监控 → 调整 TTL + 预热策略
  - 目标: L1 命中率 > 80%，L2 命中率 > 95%
- [ ] **连接池调优**:
  - HikariCP 主库/从库参数微调
  - Redis Cluster Lettuce 连接池
  - leakDetectionThreshold 检查连接泄漏
- [ ] **JVM 调优**:
  - G1GC 参数 (-XX:MaxGCPauseMillis=100)
  - 堆大小 (-Xms1g -Xmx2g)
  - Metaspace
- [ ] **异步线程池调优**:
  - 核心线程数 / 最大线程数 / 队列大小
  - MQ Consumer concurrency 参数
- [ ] **前端优化**:
  - Vite 代码分割 + tree-shaking
  - 路由懒加载
  - 图片/字体压缩
  - WS 消息合批渲染(requestAnimationFrame)

**验收标准**：
- 慢 SQL < 50ms（TOP 10 优化完）
- L1 缓存命中率 > 80%
- 无连接泄漏告警
- GC 暂停 < 100ms
- 前端首屏 < 2s (Lighthouse)

---

### Iteration 17 — Week 18：k6 压测 + 安全加固

**目标**：完整压测达标 + 安全全面加固

**Week 18 前半 — k6 完整压测**：
- [ ] 编写 k6 压测脚本 (5 个场景):
  - k6/market-load-test.js — 行情查询 500VU / 5min
  - k6/trade-load-test.js — 下单接口 200VU / 5min
  - k6/mixed-load-test.js — 混合场景 1000VU / 10min
  - k6/websocket-load-test.js — WS 10000连接 / 5min
  - k6/login-load-test.js — 登录 100VU / 3min
- [ ] 执行压测 → 达标:
  - 行情 P95 < 50ms, P99 < 100ms, 错误率 < 0.1%
  - 下单 P95 < 100ms, P99 < 200ms, 错误率 < 0.1%
  - 混合 P99 < 300ms, 错误率 < 0.5%
  - WS 推送延迟 P99 < 500ms
  - 登录 P99 < 300ms
- [ ] 压测不达标 → 定位瓶颈 → 调优 → 重测

**Week 18 后半 — 安全加固**：
- [ ] 安全测试（越权/注入/暴力破解/Token安全）
- [ ] Trivy 镜像扫描 → 修复 Critical 漏洞
- [ ] OWASP ZAP 快速扫描（可选）
- [ ] 生产环境关闭 Swagger UI（`springdoc.swagger-ui.enabled=false`）
- [ ] 生产环境 Actuator 仅暴露 health + prometheus + info
- [ ] CORS 白名单收紧
- [ ] 敏感配置 → 环境变量 / Docker Secrets
- [ ] Nginx rate limiting 参数验证

**验收标准**：
- 5 个 k6 场景全部达标
- Trivy 0 Critical
- 安全测试全部通过
- 生产环境 Swagger 不可访问
- Nginx 限流生效验证

---

### Iteration 18 — Week 19-20：打磨 + 发布

**Week 19 — Bug 修复 + UX 打磨**：
- [ ] 修复积累的 Bug
- [ ] 前端动画/过渡效果优化
- [ ] 加载状态 + 空状态 + 错误状态一致性
- [ ] 响应式布局适配（≥1200px 优先）
- [ ] favicon + 页面标题 + OG meta
- [ ] Error Boundary 组件
- [ ] Playwright E2E 测试: 注册→登录→下单→持仓 完整流程

**Week 20 — Beta 发布**：
- [ ] README.md 完善（项目介绍/截图/快速启动/技术栈/架构图/高并发设计亮点）
- [ ] CONTRIBUTING.md
- [ ] 用户操作手册（简要版）
- [ ] 确认所有环境变量文档化
- [ ] 最终全链路回归测试
- [ ] k6 最终压测（确认发布版本达标）
- [ ] Tag v0.1.0-beta
- [ ] 部署到云服务器（2+App实例 + PG主从 + Redis Cluster）
- [ ] 分享上线 🎉

---

## 三、MVP 核心功能清单

以下功能是 **MVP 最低要求**（Week 1-12 必须完成）：

| 模块 | 功能 | 优先级 | 高并发要求 |
|---|---|---|---|
| Common | 多级缓存抽象(Caffeine+Redis) | **P0** | Week 2 完成 |
| Common | 限流组件(@RateLimit) | **P0** | Week 2 完成 |
| Common | 读写分离路由 | **P0** | Week 2 完成 |
| Auth | 邮箱OTP注册(MQ异步) | **P0** | — |
| Auth | 密码登录(Caffeine锁定缓存) | **P0** | — |
| Auth | JWT签发/刷新/登出(Redis黑名单) | **P0** | — |
| User | 创建资金账户(行级锁+乐观锁) | **P0** | — |
| Market | 股票搜索(Caffeine内存搜索) | **P0** | — |
| Market | 实时行情(多级缓存L1+L2) | **P0** | L1 TTL=3s |
| Market | 行情Pub/Sub扇出 | **P0** | 多实例同步 |
| Market | K 线图(Redis L2缓存) | **P0** | — |
| Market | WebSocket推送(背压控制) | **P0** | 10000连接/实例 |
| Trade | 限价委托买入(短事务<50ms) | **P0** | 幂等+行级锁 |
| Trade | 限价委托卖出 | **P0** | T+1+FOR UPDATE |
| Trade | 撤单(乐观锁CAS) | **P0** | — |
| Trade | MQ异步撮合(乐观锁重试) | **P0** | 并发消费者=8 |
| Portfolio | 资产总览(行情走L1缓存) | **P0** | — |
| Portfolio | 持仓明细 | **P0** | — |
| Portfolio | 资金流水(分区表+从库) | **P0** | — |

---

## 四、模块依赖关系与推荐开发顺序

```
                    common + config     (Week 1)
                         │
                   多级缓存+限流+读写分离  (Week 2)  ← 高并发基础设施
                         │
                    ┌─────┴─────┐
                    ▼           ▼
                  auth        user/account    (Week 3-4)
                    │           │
                    └─────┬─────┘
                          ▼
              market (Provider+缓存+扇出+WS)  (Week 5-7)  ← 3周，高并发核心
                          │
                          ▼
              trade (下单+MQ撮合+幂等+重试)  (Week 8-9)  ← 2周，交易一致性
                          │
                          ▼
           portfolio (分区表+分批快照)      (Week 10)
                          │
               ┌──────────┼──────────┐
               ▼          ▼          ▼
           watchlist  notification  admin   (Week 13+)
                          │
                          ▼
              部署+监控+CI/CD+压测         (Week 14-18)  ← 5周，质量保障
```

---

## 五、风险与应对

| 风险 | 概率 | 影响 | 应对 |
|---|---|---|---|
| 行情 API 被封禁/限流 | 中 | 高 | 多 Provider 降级 + 断路器 + 开发阶段用 Mock |
| 一人开发进度滞后 | 高 | 高 | 严格按周计划执行，优先 P0 功能 |
| 交易一致性 Bug | 中 | 极高 | 充分单测 + 全链路集成测试 + 乐观锁 + 并发测试 |
| WebSocket 连接不稳定 | 中 | 中 | SockJS 降级 + 指数退避重连 + 背压控制 |
| 多级缓存一致性问题 | 中 | 高 | Pub/Sub 失效通知 + TTL 兜底 + stale 降级 |
| Redis Cluster 故障 | 低 | 高 | 降级为本地 Caffeine only + 告警 |
| PG 主从复制延迟 | 中 | 中 | 写后读强制主库 + 复制延迟监控(>1s告警) |
| MQ 消息堆积 | 中 | 中 | DLQ + 队列深度告警 + 消费者水平扩展 |
| k6 压测不达标 | 中 | 高 | 预留 Week 17 专门调优，瓶颈定位工具链就绪 |
| 部署环境问题 | 低 | 中 | Docker 隔离 + 本地与生产一致 |
| 数据库性能瓶颈 | 中 | 高 | 分区表 + 读写分离 + 索引优化 + 连接池调参 |

---

## 六、Scope 缩减预案

如果进度严重滞后，按以下优先级**砍功能**（高并发基础设施不可砍）：

| 优先级 | 可砍功能 | 影响 |
|---|---|---|
| 1（先砍） | OTP 登录方式 | 仅保留密码登录 |
| 2 | 忘记密码 | 手动联系管理员重置 |
| 3 | 消息通知模块 | 不推送成交通知 |
| 4 | 收益曲线 | 仅保留资产数值 |
| 5 | 自选股模块 | MVP 不含自选 |
| 6 | K 线周K/月K | 仅保留日K |
| 7 | 管理后台 | 直接操作数据库 |
| 8 | 完整压测(5场景) | 仅保留轻量压测(50VU) |
| 9 | Loki/Tempo 日志追踪 | 仅保留 Prometheus+Grafana |

**绝不可砍**：
- 注册 + 登录 + 行情查看 + 买入 + 卖出 + 撤单 + 持仓 + 资金
- 多级缓存(Caffeine+Redis) + 限流 + 读写分离（架构基础）
- MQ 异步撮合 + 幂等 + 乐观锁（交易一致性）
- WebSocket 推送（核心体验）

---

## 七、每周检查清单模板

每周五复盘时填写：

```markdown
## Week N 复盘

### 计划 vs 实际
- [ ] 任务A — ✅ 完成 / 🟡 进行中 / ❌ 延期
- [ ] 任务B — ...

### 指标
- 新增代码行数: ___
- 新增测试用例: ___
- 测试覆盖率: ___%
- Bug 数量: ___

### 高并发指标 (Week 14+ 开始填写)
- L1 缓存命中率: ___%
- L2 缓存命中率: ___%
- WS 连接数峰值: ___
- 行情 P99: ___ms
- 交易 P99: ___ms
- 压测通过场景: ___/5

### 遇到的问题
1. ...

### 下周计划调整
1. ...
```

---

## 八、里程碑总结

| 里程碑 | 时间 | 内容 |
|---|---|---|
| **M0 — 骨架+基础设施就绪** | Week 2 末 | 项目可运行，多级缓存+限流+读写分离可用，PG主从+Redis+Nginx就绪 |
| **M1 — 可登录** | Week 3 末 | 注册+登录+JWT全流程，Caffeine锁定缓存，MQ异步邮件 |
| **M2 — 可看盘** | Week 7 末 | 搜索+行情+K线+多级缓存+Pub/Sub扇出+WS推送+背压控制 |
| **M3 — 可交易** | Week 9 末 | 下单+撤单+MQ异步撮合+幂等+乐观锁重试+成交+持仓 |
| **M4 — 前端 MVP** | Week 12 末 | 全栈可用（认证+行情+交易+持仓+WS实时推送） |
| **M5 — 功能完整** | Week 13 末 | 自选股+通知+推送 |
| **M6 — 可部署(高可用)** | Week 14 末 | Docker集群(2+App)+Nginx LB+PG主从+Redis Cluster |
| **M7 — 可观测** | Week 15 末 | Prometheus+Grafana+Loki+Tempo+告警规则 |
| **M8 — CI/CD就绪** | Week 16 末 | GitHub Actions+轻量压测门禁+镜像安全扫描 |
| **M9 — 性能达标** | Week 18 末 | k6全场景压测达标+安全加固 |
| **M10 — Beta 发布** | Week 20 末 | 线上可访问 v0.1.0-beta，高并发架构就绪 |
