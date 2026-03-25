# 股市仿真交易系统 — GitHub Copilot 仓库级指令

> 本文件是仓库级 Copilot 指令，等价于 Agent Skills 配置包。
> 放置于 `.github/copilot-instructions.md`，Copilot Chat 会自动读取并遵守。
> v2.0：整合百万用户高并发硬规则

---

## 一、技术栈默认假设（全局上下文）

| 层次 | 技术 | 版本 |
|---|---|---|
| 语言 | Java | 21 LTS |
| 后端框架 | Spring Boot | 3.2.x |
| 前端框架 | Vue 3 + TypeScript + Vite | Vue 3.4+ / Vite 5+ |
| 数据库 | PostgreSQL (主从读写分离) | 16 |
| L1 缓存 | Caffeine (JVM 本地) | 3.1+ |
| L2 缓存 | Redis Cluster (3主3从, Lettuce) | 7.x |
| 消息队列 | RabbitMQ | 3.13 |
| ORM | MyBatis-Plus | 3.5.x |
| 安全 | Spring Security 6 + JWT (access 30min + refresh 7d) | — |
| 接口文档 | SpringDoc OpenAPI 3 | 2.3+ |
| 图表 | ECharts 5 | — |
| 实时推送 | WebSocket (STOMP over SockJS) | — |
| 负载均衡 | Nginx (反向代理+限流+WS粘性) | — |
| 构建 | Maven 3.9+ / pnpm 8+ | — |
| 容器 | Docker + docker-compose | — |
| CI/CD | GitHub Actions | — |
| 日志 | SLF4J + Logback → JSON → Loki | — |
| 监控 | Micrometer + Prometheus + Grafana | — |
| 链路追踪 | Micrometer Tracing → Tempo | — |
| 代码工具 | Lombok, MapStruct 1.5, Spotless | — |
| 数据库迁移 | Flyway 10 | — |
| 测试 | JUnit5, Mockito, Testcontainers, REST Assured | — |
| 压测 | k6 | — |
| E2E | Playwright | — |

---

## 二、分层与依赖规则（DDD-lite 四层架构）

### 2.1 分层定义

```
controller  →  application(service)  →  domain  →  infrastructure
     ↓               ↓                    ↑              ↑
   DTO/VO         Command/Query        Entity         Repo实现/Gateway
```

### 2.2 硬性规则

1. **domain 层禁止依赖任何框架注解**（不含 Spring/MyBatis/Redis）；领域实体使用纯 Java POJO。
2. **Repository 接口定义在 domain 层**，实现放在 infrastructure 层。
3. **controller 层只做参数校验 + 调用 application service + 返回统一 Result<T>**。
4. **application service 编排领域逻辑**，不包含业务规则；业务规则内聚在 domain entity / domain service。
5. **跨模块调用通过 application 层的接口**（不可直接引用另一个模块的 domain）。
6. **DTO/VO/Command/Query 按层隔离**，不可穿透到 domain。
7. **统一响应格式**：`Result<T>{ code, message, data, traceId, timestamp }`。
8. **统一异常体系**：`BizException(ErrorCode)` → 全局 `@RestControllerAdvice` 捕获。
9. **ErrorCode 枚举集中管理**，格式 `MODULE_SCENE_REASON`，如 `TRADE_ORDER_INSUFFICIENT_FUND`。

### 2.3 包命名规范

```
com.lzbsdsg.stocksimulation
├── common/            # 通用：Result, ErrorCode, 异常, 多级缓存抽象, 工具
├── config/            # 全局配置：Security, WebSocket, Redis, Caffeine, DataSource, Swagger
├── auth/              # 认证模块
│   ├── controller/
│   ├── application/
│   ├── domain/
│   └── infrastructure/
├── user/              # 用户与账户模块
├── market/            # 行情模块（多级缓存 + Pub/Sub扇出）
├── trade/             # 交易模块（订单、撮合、撤单）
├── portfolio/         # 持仓与资产模块（分批快照）
├── watchlist/         # 自选股模块
├── notification/      # 消息推送模块（WebSocket背压）
└── admin/             # 管理后台模块
```

---

## 三、高并发硬规则

### 3.1 多级缓存规则

| 规则 | 说明 |
|---|---|
| **L1 = Caffeine (JVM 本地)** | 行情快照 TTL=3s max=5000；股票列表 TTL=5min；配置 TTL=10min |
| **L2 = Redis Cluster** | 行情快照 TTL=5s+随机偏移(0~500ms)防雪崩；历史K线不走 Redis 缓存 |
| **读路径** | L1 → L2 → Provider回源；L2 命中时回填 L1 |
| **写路径** | 先写 L2，再通过 Redis Pub/Sub 通知所有实例更新 L1 |
| **缓存失效** | Redis Pub/Sub `cache:invalidate:{region}` 通知所有实例删除 L1 |
| **防穿透** | 空值缓存 TTL=30s；Bloom Filter 可选 |
| **防雪崩** | TTL 加随机偏移；热点 key 提前续期 |
| **防击穿** | 同一 key 分布式锁回源（Redis SETNX TTL=3s），仅一个实例回源 |
| **响应头** | `X-Cache-Status: HIT-L1 / HIT-L2 / MISS / STALE` |

### 3.2 行情推送扇出规则

| 规则 | 说明 |
|---|---|
| **拉取选主** | MarketIngestService 持有 Redis 分布式锁，仅一个实例拉取行情 |
| **广播** | 拉取结果写入 Redis L2 + 发布 Pub/Sub `market:quote:broadcast` |
| **扇出** | 所有 App 实例订阅 → 更新本地 L1 → 推送各自管理的 WS 连接 |
| **WS 粘性** | Nginx `ip_hash` 保证同一用户 WS 连接到同一 App 实例 |
| **连接上限** | 单实例 10000 WS 连接，超限返回 503 |

### 3.3 WebSocket 背压规则

| 场景 | 策略 |
|---|---|
| 推送队列深度 > 100 | 丢弃该连接最旧消息 |
| 推送延迟 > 5s | 降级为 10s 推送周期 |
| 发送缓冲区 > 64KB | 暂停推送，等缓冲区消化 |
| 连接总数 > 8000/实例 | 告警（扩容提醒），>10000 拒绝新连接 |

### 3.4 读写分离规则

| 规则 | 说明 |
|---|---|
| **数据源路由** | `AbstractRoutingDataSource`，基于 `@ReadOnly` 注解或 ThreadLocal 标记 |
| **写操作** | 默认走主库，含 `@Transactional` 的方法强制主库 |
| **查询操作** | 标记 `@ReadOnly` 的方法路由到从库 |
| **典型从库场景** | 委托列表、成交记录、资金流水、收益曲线、管理后台查询 |
| **主库保护** | maximumPoolSize=30；从库 maximumPoolSize=50 |
| **复制延迟** | 告警阈值 > 1s；写后立即读场景强制走主库 |

### 3.5 异步处理管线规则

| 规则 | 说明 |
|---|---|
| **撮合** | 下单后 MQ 异步撮合，不阻塞 HTTP 响应 |
| **邮件** | OTP/通知邮件走 MQ 异步发送 |
| **成交通知** | 撮合成功后 MQ fanout → WebSocket 推送 / 站内信 |
| **MQ 消费者** | prefetch=10, concurrentConsumers=8 |
| **DLQ** | 消费失败重试 3 次后进入死信队列，人工处理 |
| **分批处理** | 收盘快照每批 500 用户，分布式锁防重复 |

### 3.6 限流规则

| 层次 | 限流规则 | 实现 |
|---|---|---|
| Nginx 全局 | 单 IP 100 req/s | `limit_req_zone $binary_remote_addr` |
| 应用全局 | 单用户 100 req/min | Redis + Lua 令牌桶 |
| 交易接口 | 单用户 10 req/min | `@RateLimit` 注解 |
| 行情接口 | 单用户 60 req/min | `@RateLimit` 注解 |
| OTP 发送 | 1/60s/email + 20/h/ip | Redis SETNX |
| WebSocket | 10000 连接/实例 | 连接注册表计数 |

### 3.7 容量规划约束

| 指标 | 目标值 |
|---|---|
| 注册用户 | 100 万 |
| DAU | 10 万 |
| 峰值在线 | 5 万 |
| 峰值下单 TPS | ≥ 3000 |
| 行情推送连接 | ≥ 5 万 |
| 可用性 | ≥ 99.9% (月度) |
| 行情 P95/P99 | < 50ms / < 100ms |
| 交易 P95/P99 | < 100ms / < 200ms |
| 撮合 P99 | < 100ms |
| 错误率 | < 0.1% (非业务错误) |

---

## 四、交易一致性硬规则

### 4.1 下单流程（买入）

```
1. Nginx限流通过
2. JWT校验 (Redis黑名单检查)
3. 幂等检查：clientOrderId Redis SETNX(5min)
4. 前置校验（全部走Caffeine缓存，不访问DB）：
   a. 交易时间 ✓
   b. 涨跌停 ✓
   c. 100股整数倍 ✓
5. 计算冻结金额 = 委托价 × 数量 + 预估手续费
6. 开启DB事务（短事务，目标<50ms）：
   a. SELECT ... FOR UPDATE 只锁当前用户Account行（非表锁）
   b. 检查可用资金 ≥ 冻结金额
   c. 扣减可用资金，增加冻结资金
   d. 插入 Order(status=PENDING)
   e. 插入 FundFlow(type=FREEZE)
7. 提交事务
8. 异步发送MQ撮合消息（不阻塞响应）
```

### 4.2 撮合成交

```
1. MQ Consumer获取消息（prefetch=10, 并发消费者=8）
2. 幂等检查：orderId是否已撮合
3. 获取最新行情价（L1 → L2 → Provider）
4. 判断委托价是否可成交（买入：委托价 ≥ 市价；卖出：委托价 ≤ 市价）
5. 开始事务：
   a. 生成 Trade 记录
   b. 更新 Order status → FILLED（乐观锁 WHERE version = ?）
   c. 更新 Position（加减持仓、计算成本价）（乐观锁）
   d. 更新 Account（解冻资金 / 扣费 / 入账）（乐观锁）
   e. 插入 FundFlow(type=TRADE)
6. 提交事务
7. 发送成交事件MQ（fanout → 通知推送/站内信）
8. 乐观锁冲突 → 重试（最多3次, 50ms × 2^n 退避）
```

### 4.3 硬性约束

- **幂等**：Order 使用 `clientOrderId`（UUID）做幂等键，Redis SETNX 5min + DB unique index。
- **乐观锁**：Account、Position 表必须有 `version` 字段，更新时 `WHERE version = ?`。
- **事务边界**：一笔订单的资金变动 + 持仓变动 + 流水记录必须在同一个 `@Transactional` 内。
- **事务时长**：DB 事务目标 < 50ms，禁止事务内调用外部服务（HTTP/MQ）。
- **防超卖**：卖出时校验 `available_quantity ≥ 卖出数量`，用 `FOR UPDATE`。
- **行级锁**：`SELECT ... FOR UPDATE` 仅锁当前用户的 Account/Position 行，不同用户完全并行。
- **T+1**：买入当日持仓标记 `frozen_until = T+1 收盘`，不可卖出。
- **重试**：乐观锁冲突最多重试 3 次，间隔 50ms 指数退避。
- **前置校验走缓存**：交易时间/涨跌停/配置参数从 Caffeine 读取，不访问 DB。

---

## 五、行情 Provider 抽象规则

### 5.1 接口抽象

```java
public interface MarketDataProvider {
    QuoteSnapshot getQuote(String stockCode);
    List<KLinePoint> getKLine(String stockCode, KLinePeriod period, LocalDate from, LocalDate to);
    List<QuoteSnapshot> batchGetQuotes(List<String> stockCodes);
    boolean isAvailable();  // 健康检查
}
```

### 5.2 实现要求

| 实现类 | 说明 |
|---|---|
| `SinaMarketDataAdapter` | 新浪财经接口适配 |
| `TencentMarketDataAdapter` | 腾讯财经接口适配 |
| `MockMarketDataAdapter` | 本地 Mock 数据，开发/测试用 |

### 5.3 多级缓存与降级

- **L1 缓存**：Caffeine JVM 本地，行情快照 TTL=3s max=5000，K 线不进 L1。
- **L2 缓存**：Redis Cluster，行情快照 TTL=5s+random(0,500ms)，历史K线不进 Redis。
- **历史K线**：真实日K按需增量入 PostgreSQL，同一股票同一自然日最多同步一次，仅保留最近3年。
- **读路径**：L1 → L2 → Provider 回源；L2 命中时回填 L1。
- **节流**：同一股票 3s 内重复回源请求，分布式锁排队，仅单实例回源。
- **批量**：前端自选股列表用 `batchGetQuotes`，一次请求最多 50 只。
- **降级**：主 Provider 失败 → 断路器(CLOSED→OPEN→HALF_OPEN) → 备 Provider → stale 缓存 → 错误码。
- **异常**：Provider 抛出 `MarketDataUnavailableException`，上层捕获返回降级数据或错误码。
- **扇出**：MarketIngestService 分布式锁选主拉取 → Redis Pub/Sub → 所有实例更新 L1 + 推 WS。

---

## 六、安全硬规则

### 6.1 邮箱 OTP

- 验证码 6 位纯数字，Redis 存储 `otp:{email}` → hash(code)，TTL = 5 分钟。
- **频率限制**：同一邮箱 60s 内只能发一次；同一 IP 每小时最多 20 次。
- **防刷**：接口加图形验证码（首次可选，触发限流后强制）。
- 验证码验证后立即删除 Redis key（一次性）。
- **邮件异步**：发送走 RabbitMQ 异步，API 立即返回。

### 6.2 密码策略

- 最少 8 位，必须包含大写+小写+数字。
- BCrypt 加密存储，cost factor = 12。
- 登录连续失败 5 次锁定 30 分钟（Caffeine + Redis 双写，快速拒绝）。

### 6.3 JWT

- Access Token：30 分钟，Header `Authorization: Bearer <token>`。
- Refresh Token：7 天，httpOnly cookie 或 body 传递。
- 刷新时旧 Refresh Token 立即失效（Rotation）。
- 登出时将 Access Token 加入 Redis 黑名单至过期。

### 6.4 接口限流

- Nginx 全局：单 IP 100 req/s（`limit_req_zone`）。
- 应用全局：单用户 100 req/min（Redis + Lua 令牌桶，`@RateLimit` AOP）。
- 交易接口：单用户 10 req/min。
- 行情接口：单用户 60 req/min。
- 响应头：`X-RateLimit-Limit` / `X-RateLimit-Remaining` / `X-RateLimit-Reset`。

---

## 七、输出格式硬约束

**当用户要求生成模块 / 方案 / 代码时，必须依次输出以下章节：**

1. **Assumptions** — 列出所有工程假设与边界（含高并发容量假设）
2. **目录树** — 具体到包名/文件名
3. **核心类职责** — 每个类的单一职责说明
4. **API 契约** — path / method / 请求体 / 响应体 / 错误码 / SLO / 限流 / 缓存策略
5. **Flyway SQL** — 建表 / 索引 / 分区 / 初始数据（版本号 `V{yyyyMMdd}_{seq}__{desc}.sql`）
6. **事务与一致性** — 事务边界、锁策略(行级锁)、幂等方案、乐观锁重试
7. **缓存与性能** — 多级缓存配置、读写分离标注、异步处理管线
8. **测试策略** — 每个核心流程至少给出 Happy Path + 2 个边界 Case 的测试方法签名（含并发测试）

---

## 八、测试与质量门禁规则

### 8.1 分层测试

| 层次 | 工具 | 规则 |
|---|---|---|
| 单元测试 | JUnit5 + Mockito | domain 层覆盖率 ≥ 90%；service 层 ≥ 80% |
| 集成测试 | Testcontainers (PG + Redis) | 每个 Repository 至少 CRUD + 边界 case |
| 接口测试 | REST Assured + MockMvc | 每个 API 至少 Happy + 401 + 400 + 业务异常 |
| 并发测试 | JUnit5 + CountDownLatch | 下单/撤单并发安全验证 |
| 前端单测 | Vitest | 工具函数/Store 覆盖率 ≥ 70% |
| E2E | Playwright | 核心流程（注册→登录→下单→持仓）至少 1 条 |
| 性能-轻量 | k6 (CI每次PR) | 50VU / 1min，P99 < SLO × 2 |
| 性能-完整 | k6 (每周定时) | 按压测标准执行（见下表） |

### 8.2 k6 压测通过标准

| 场景 | 并发 | 持续 | P95 | P99 | 错误率 |
|---|---|---|---|---|---|
| 行情查询 | 500 VU | 5min | < 50ms | < 100ms | < 0.1% |
| 下单接口 | 200 VU | 5min | < 100ms | < 200ms | < 0.1% |
| 混合场景 | 1000 VU | 10min | — | < 300ms | < 0.5% |
| WS连接 | 10000连接 | 5min | — | 推送延迟 < 500ms | — |
| 登录 | 100 VU | 3min | — | < 300ms | < 0.1% |

### 8.3 质量门禁（CI 必须通过）

- 单测全部 GREEN
- JaCoCo 行覆盖率 ≥ 70%
- Spotless 格式检查通过
- 无 Critical / Blocker 级别的 SonarQube issue
- Docker 镜像构建成功
- 依赖漏洞扫描（Trivy）无 Critical
- 轻量压测通过（50VU / 1min）

---

## 九、数据库规范

### 9.1 命名

- 表名：`t_{module}_{entity}`，如 `t_user_account`、`t_trade_order`。
- 字段：`snake_case`，主键统一 `id BIGSERIAL`。
- 审计字段：所有表必须有 `created_at TIMESTAMPTZ`、`updated_at TIMESTAMPTZ`。
- 逻辑删除：需要时用 `deleted_at TIMESTAMPTZ`（NULL = 未删除）。
- 乐观锁字段：金融相关表必须有 `version INTEGER DEFAULT 0`。

### 9.2 索引策略

- 高频查询字段建 B-tree 索引。
- 组合查询建联合索引，遵循最左前缀。
- `status` + `created_at` 常见组合索引。
- 不在大结果集上用 `LIKE '%xxx%'`，需全文搜索用 PG `tsvector`。

### 9.3 分区策略

- `t_portfolio_fund_flow` — 按月范围分区 (PARTITION BY RANGE(created_at))
- `t_portfolio_asset_snapshot` — 按月范围分区 (PARTITION BY RANGE(snapshot_date))
- `t_trade_deal` — 按月范围分区 (PARTITION BY RANGE(traded_at))
- 分区自动创建：Flyway 迁移脚本 + 定时任务提前创建下月分区

### 9.4 读写分离

- 写操作 → 主库（maximumPoolSize=30）
- 查询操作 → 从库（maximumPoolSize=50）
- 写后立即读 → 强制主库（通过 ThreadLocal 标记）
- 复制延迟告警阈值：> 1s

### 9.5 连接池

- HikariCP，主库 min=10 max=30，从库 min=20 max=50
- `connectionTimeout=3000ms`, `idleTimeout=600000ms`, `maxLifetime=1800000ms`
- `leakDetectionThreshold=30000ms`

### 9.6 Flyway

- 目录：`src/main/resources/db/migration/`。
- 命名：`V{yyyyMMdd}_{seq}__{description}.sql`，如 `V20260213_001__create_user_tables.sql`。
- 禁止修改已执行的迁移文件，只可追加。

---

## 十、前端规范

### 10.1 目录结构

```
stock-simulation-web/
├── src/
│   ├── api/          # Axios 封装 + 按模块 API
│   ├── assets/       # 静态资源
│   ├── components/   # 通用组件
│   ├── composables/  # 组合式函数（useWebSocket含背压检测）
│   ├── layouts/      # 布局
│   ├── pages/        # 页面路由
│   ├── router/       # Vue Router
│   ├── stores/       # Pinia Store
│   ├── types/        # TypeScript 类型
│   └── utils/        # 工具函数
├── public/
├── index.html
├── vite.config.ts
├── tsconfig.json
└── package.json
```

### 10.2 规则

- 组件命名 PascalCase，文件名同组件名。
- Pinia Store 按模块拆分：`useAuthStore`, `useMarketStore`, `useTradeStore`。
- API 层统一拦截 401 → 自动 refresh token → 重试原请求。
- WebSocket 连接由 `useMarketStore` 管理，断线自动重连，指数退避（1s→2s→4s→8s→16s→30s max）。
- WebSocket 背压检测：推送延迟 > 5s 时降级显示频率。
- 行情数据优先展示缓存，检查 `X-Cache-Status` 头判断数据新鲜度。

---

## 十一、Git 与分支规范

- `main`：生产就绪。
- `develop`：集成分支。
- `feature/{module}-{desc}`：功能分支，如 `feature/trade-order-create`。
- `fix/{issue-id}-{desc}`：修复分支。
- Commit Message：`type(scope): description`，type ∈ {feat, fix, refactor, docs, test, chore, perf}。
- `perf` type 用于性能优化相关提交。
