# 文档 A：详细设计文档

> 版本：2.0 | 日期：2026-02-13 | 状态：初稿
> 变更：v2.0 覆盖式整合百万用户高并发架构设计

---

## 一、背景与目标

### 1.1 背景

个人投资者在进入真实股票市场前，缺乏安全的练习环境。市面上的模拟炒股平台要么功能简陋，要么数据延迟严重，要么注册流程繁琐。本项目旨在构建一个**规则仿真度高、体验流畅、架构可扩展、支撑百万用户高并发**的股市仿真交易系统。

### 1.2 核心目标

- 提供接近真实 A 股规则的模拟交易体验
- 接入实时行情（允许数秒延迟）
- 提供完整的资产管理与收益分析功能
- 架构支持水平扩展，支撑百万注册用户、10 万日活、5 万同时在线
- 核心链路高性能：交易 P99 < 200ms，行情 P99 < 100ms

### 1.3 范围

- **In Scope**：A 股模拟交易（普通股连续竞价）、行情展示、资产管理、自选股、WebSocket推送、多级缓存、读写分离、水平扩展
- **Out of Scope**：融资融券、期权期货、量化策略、集合竞价、多市场

### 1.4 非目标

- 不连接真实券商系统
- 不影响真实市场
- MVP 阶段不支持自动交易

---

## 二、假设与边界

| # | 假设 |
|---|---|
| 1 | 系统设计支撑百万注册用户、10 万 DAU、5 万峰值在线；MVP 阶段可单机部署(2C4G)，架构预留水平扩展能力 |
| 2 | 行情延迟 ≤ 5 秒可接受（非实时快照） |
| 3 | 不模拟集合竞价（9:15-9:25），仅模拟连续竞价（9:30-15:00） |
| 4 | 撮合采用「即时成交」简化模型：委托价满足条件即按市价成交 |
| 5 | 不支持部分成交（一笔订单要么全部成交 / 要么不成交） |
| 6 | 手续费规则可配置，默认：佣金万三(最低5元) + 印花税千一(仅卖出) + 过户费十万分之一 |
| 7 | 邮件服务使用 SMTP（初期可用 QQ/163 企业邮箱，后续可换 SendGrid/SES），发送走 MQ 异步 |
| 8 | 初始资金范围 10,000 ~ 1,000,000，创建后不可修改 |
| 9 | 涨跌停规则：普通股 ±10%，ST股 ±5%，科创板/创业板 ±20% |
| 10 | 交易时间：工作日 9:30-11:30, 13:00-15:00（通过配置表管理，Caffeine 缓存） |
| 11 | 多级缓存：Caffeine(L1, JVM本地) + Redis Cluster(L2, 分布式)，行情快照 L1 TTL=3s |
| 12 | 读写分离：PostgreSQL 主从复制，写操作走主库，读操作走从库 |
| 13 | WebSocket 单实例连接上限 10000，超限拒绝新连接 |
| 14 | 峰值下单 TPS 目标 ≥ 3000，通过行级锁(非表锁)保证不同用户完全并行 |
| 15 | 用户资料维护仅支持昵称修改，不提供头像上传 |
| 16 | 历史K线采用真实日K按需入库策略：仅在访问该股票详情时触发增量同步，同一股票同一天最多同步一次，仅保留最近3年数据 |

---

## 三、SLO（服务级别目标）

| 指标 | 目标 | 测量方式 |
|---|---|---|
| 可用性 | ≥ 99.9%（月度） | Prometheus uptime |
| 行情读接口 P95 | < 50ms | Micrometer histogram |
| 行情读接口 P99 | < 100ms | Micrometer histogram |
| 交易写接口 P95 | < 100ms | Micrometer histogram |
| 交易写接口 P99 | < 200ms | Micrometer histogram |
| 撮合延迟 P99 | < 100ms | 自定义 Timer |
| WebSocket 推送延迟 | < 500ms（端到端） | 客户端上报 |
| 下单吞吐量 | ≥ 3000 TPS（峰值） | k6 压测 |
| 行情推送吞吐 | ≥ 5 万连接 × 3s/次 | 压测 |
| 错误率 | < 0.1%（非业务错误） | 5xx / total |

---

## 四、需求拆解

### 4.1 用户故事

| # | 故事 | 优先级 |
|---|---|---|
| US-01 | 作为新用户，我可以用邮箱验证码注册账号并设置密码 | P0 |
| US-02 | 作为用户，我可以用密码登录，也可以用邮箱验证码登录 | P0 |
| US-03 | 作为用户，我在忘记密码时可以通过邮箱重置密码 | P0 |
| US-04 | 作为用户，注册时我可以选择初始资金（10000~1000000） | P0 |
| US-05 | 作为用户，我可以搜索股票并查看实时行情（价格/涨跌幅/成交量）| P0 |
| US-06 | 作为用户，我可以查看股票的 K 线图（日K / 周K / 月K） | P0 |
| US-07 | 作为用户，我可以按限价委托买入股票 | P0 |
| US-08 | 作为用户，我可以卖出持仓股票 | P0 |
| US-09 | 作为用户，我可以撤销未成交的委托 | P0 |
| US-10 | 作为用户，我可以查看资产总览（总资产/可用/冻结/市值） | P0 |
| US-11 | 作为用户，我可以查看持仓明细（成本/现价/盈亏） | P0 |
| US-12 | 作为用户，我可以查看交易记录和资金流水 | P0 |
| US-13 | 作为用户，我可以添加自选股并实时查看行情 | P1 |
| US-14 | 作为用户，我可以收到成交通知 | P1 |
| US-15 | 作为用户，我可以查看收益曲线和最大回撤 | P1 |
| US-16 | 作为用户，我可以查看排行榜 | P2 |
| US-17 | 作为管理员，我可以管理用户和系统配置 | P3 |
| US-18 | 作为用户，我可以修改昵称并在资料页立即看到更新 | P1 |

### 4.2 核心流程

#### 注册流程

```
用户输入邮箱 → 发送OTP(Redis TTL=5min, 邮件走MQ异步发送) → 用户输入OTP+密码+昵称+初始资金
→ 验证OTP → 创建User → 创建Account(初始资金) → 签发JWT → 返回Token
```

#### 登录流程

```
用户输入邮箱+密码 → Caffeine缓存检查锁定状态 → 查询User → BCrypt验证
成功 → 清零失败计数 → 签发JWT(access 30min + refresh 7d)
失败 → 失败计数+1 → 达到5次锁定30min → 写入Caffeine缓存
```

#### 用户资料修改流程

```
用户在资料页填写新昵称
→ 前端调用 PUT /api/v1/user/me
→ 后端鉴权并校验昵称长度
→ 更新 t_user.nickname
→ 返回最新用户资料
→ 前端刷新页面显示昵称
```

#### 行情获取流程（多级缓存路径）

```
前端请求行情 → Controller → MarketDataFacade
→ L1 Caffeine检查(TTL=3s, 命中率期望>80%) → 命中直接返回(<1ms)
→ L2 Redis检查(TTL=5s) → 命中 → 回填L1 → 返回(<5ms)
→ 回源 → 主Provider(Sina) → 成功写L1+L2 → 返回
→ 主Provider失败 → 断路器检查 → 备Provider(Tencent) → 成功写缓存返回
→ 全部失败 → 返回Redis stale缓存(标记stale) 或 错误码
```

#### 可见股票集合驱动流程（2026-04 更新）

```
前端进入行情/自选/详情页面
→ 计算当前可见股票集合（最多几十只）
→ 每1.5s 调用 POST /api/v1/market/visible-codes 上报
→ Redis ZSet 记录 stockCode 最后上报时间
→ MarketIngestService 每1s执行抓取：
   a. 优先抓 active-window 内活跃股票
   b. 再抓 round-robin 全市场轮巡股票
→ 抓取成功后写 L1/L2 + Pub/Sub 广播 + WS 推送
→ 页面先读缓存快照，再接收增量推送
```

**设计意图**：
- 将抓取对象从“固定少量股票”升级为“全站用户当前可见股票并集”。
- 在用户量高、可见股票差异大时，通过股票去重与批量抓取收敛回源压力。
- 通过轮巡补齐降低长尾股票冷启动概率。

**关键参数**：
- `market.ingest.pull-interval-ms=1000`
- `market.ingest.active-window-ms=8000`
- `market.ingest.active-batch-size=800`
- `market.ingest.round-robin-batch-size=100`

#### 行情中心分页与榜单聚合流程（2026-04 更新）

```
前端打开 MarketPage
→ 先拉取股票池（/market/listed，分批加载全量代码）
→ 前端本地分页切片（30/40 每页）
→ 后端官方代理用于：
   a. 大盘数据（GET /market/indexes）
   b. 涨幅榜/跌幅榜（GET /market/rank-board）
→ 前端展示口径：
   a. 行情中心卡片仅展示当前页股票
   b. 仪表盘关注股票仅展示自选股
→ 前端实时订阅口径（并集去重）：
   自选股 + 当前页股票 + 热点集合
→ 通过 WS + visible-codes 心跳维持 1~2 秒更新

若官方源不可用：
→ 后端优先返回最近一次成功抓取缓存
→ 后端并发调用新浪/腾讯等 Provider 获取候选行情
→ 使用统一融合策略（新鲜度 + 完整度 + 有效变化）构建回退结果
→ 分页仍按本地股票池切页
→ 调用 /market/quotes 拉取当前页实时行情
→ 榜单由后端在采样股票池上计算涨幅榜/跌幅榜，并缓存最近成功结果
```

**设计约束**：
- 股票列表分页固定走前端本地分页（`/market/listed`）。
- 大盘与涨跌榜统一经后端代理官方源，降低前端网络环境差异影响。
- 官方源失败时，后端负责多源融合与缓存兜底，前端不承担榜单降级排序。
- 前端展示集合与实时订阅集合解耦，避免“页面展示变化”误伤用户自选实时链路。

#### 行情链路实时观测流程（2026-04 更新）

```
客户端 / 运维脚本
→ GET /api/v1/market/realtime-metrics
→ MarketApplicationService 聚合：
    a. Redis 活跃池统计（activeCodeCount）
    b. MarketIngestService 最近一次抓取状态
    c. MarketWebSocketHandler 连接与队列状态
    d. Micrometer Timer 快照（ingest/pubsub/ws队列/ws发送）
→ 返回统一 Result<T>
```

**接口契约（简化）**：
- Path：`/api/v1/market/realtime-metrics`
- Method：`GET`
- 鉴权：沿用行情接口鉴权策略
- 限流：`@RateLimit(limit=60, window=60, key="market:realtime-metrics")`
- 响应：
   - `activeCodeCount`
   - `lastIngestCodeCount`
   - `lastPublishedQuoteCount`
   - `lastIngestDurationMs`
   - `wsActiveConnections` / `wsQueuedTasks` / `wsDegradedMode` / `wsDroppedTotal`
   - `ingestCycleLatency` / `pubSubFanoutLatency` / `wsQueueLatency` / `wsPushLatency`

**设计意图**：
- 让“实时性是否达标”具备单接口可观测性，减少临时查日志与多系统跳转。
- 将链路拆分为抓取、扇出、排队、发送四段，便于快速定位瓶颈。

#### 历史K线获取流程（真实日K + 按需增量）

```
前端请求 /market/kline/{code}?period&from&to
→ Controller → MarketDataFacade → HistoricalKLineService
→ 归一化代码 + 区间裁剪(最多最近3年, to<=today)
→ 检查该股票日K存量:
   - 首次访问: 拉取该区间真实日K并 upsert 入库
   - 非首次访问: 仅补 latest+1 到 to 的增量区间
→ 若 to=today: 检查同步状态表，确保同一股票同一自然日最多同步一次
→ 清理3年窗口外历史数据
→ period=DAILY 直接返回日K
→ period=WEEKLY/MONTHLY 基于日K聚合后返回
```

#### 买入下单流程（高并发优化路径）

```
1. Nginx限流通过(10/min/user)
2. JWT校验(Redis黑名单检查)
3. 幂等检查：clientOrderId Redis SETNX(5min) — O(1)
4. 前置校验（全部走缓存，不访问DB）：
   a. 交易时间 ✓ — Caffeine缓存
   b. 涨跌停 ✓ — Caffeine行情缓存
   c. 100股整数倍 ✓ — 纯计算
5. 计算冻结金额 = 委托价 × 数量 + 预估手续费
6. 开启DB事务（短事务，目标<50ms）：
   a. SELECT ... FOR UPDATE 只锁当前用户Account行
   b. 校验 available_balance ≥ 冻结金额
   c. UPDATE Account: available -= 冻结, frozen += 冻结
   d. INSERT Order(status=PENDING)
   e. INSERT FundFlow(type=FREEZE)
7. 提交事务
8. 异步发送RabbitMQ撮合消息（不等待结果）
```

#### 撮合成交流程

```
1. MQ Consumer获取撮合消息（并发消费者=8, prefetch=10）
2. 幂等检查：orderId是否已撮合
3. 获取最新行情价（L1 Caffeine → L2 Redis → Provider）
4. 判断成交条件：
   - 买入：order.price >= currentPrice → 按 currentPrice 成交
   - 卖出：order.price <= currentPrice → 按 currentPrice 成交
5. 开始事务：
   a. 计算实际手续费(佣金+印花税+过户费)
   b. 生成 Trade 记录
   c. 更新 Order(status=FILLED, filledQuantity=quantity) — 乐观锁
   d. 更新 Position（加减持仓 + 重算加权平均成本价）— 乐观锁
   e. 更新 Account（解冻 + 扣费/入账）— 乐观锁
   f. 插入 FundFlow(type=TRADE)
6. 提交事务
7. 发送成交事件MQ（fanout → 通知推送/站内信/异步对账）
8. 乐观锁冲突 → 重试（最多3次, 50ms × 2^n）
```

#### 卖出下单流程

```
1. 校验：交易时间 ✓、涨跌停 ✓、100股整数倍 ✓（均走Caffeine缓存）
2. 幂等检查同买入
3. 开始事务：
   a. SELECT ... FOR UPDATE 锁定 Position
   b. 校验 available_quantity ≥ 卖出数量
   c. 校验 T+1（frozen_until 是否已过）
   d. Position: available -= 数量, frozen += 数量
   e. 插入 Order(status=PENDING, side=SELL)
4. 提交事务
5. 异步发送MQ → 撮合
```

#### 撤单流程

```
1. 查询 Order → 校验 status = PENDING
2. CAS 更新 Order.status = CANCELLED (WHERE status = PENDING AND version = ?)
3. 失败(已被撮合) → 返回撤单失败
4. 成功 → 开始事务：
   a. 买入撤单：Account 解冻资金
   b. 卖出撤单：Position 解冻持仓
   c. 插入 FundFlow(type=UNFREEZE)
5. 提交事务
```

#### 每日收盘结算流程（分批处理）

```
1. 15:00 定时任务触发（Quartz, 分布式锁防重复执行）
2. 过期订单：分批查询PENDING订单(每批200) → EXPIRED → 解冻资金/持仓
3. T+1 更新：分批更新今日买入持仓，frozen_until = 下一交易日15:00
4. 历史归档：03:30 分批归档终态订单（每批500，主表保留近7天）
   a. 仅归档 `CANCELLED/EXPIRED/REJECTED` 且无成交明细订单
   b. 搬迁至 `t_trade_order_archive` 后从主表删除
   c. 历史查询统一合并主表+归档表
5. 资产快照：分批处理用户(每批500)
   a. 查询用户账户 + 持仓
   b. 批量获取行情（batchGetQuotes, L1→L2→Provider）
   c. 计算总资产 = 可用资金 + 冻结资金 + 持仓市值(按收盘价)
   d. 收益率 = (总资产 - 初始资金) / 初始资金 × 100%
   e. 批量INSERT AssetSnapshot
6. 快照幂等：UNIQUE(user_id, snapshot_date)
7. 全程异步执行，不阻塞 Web 请求线程
```

---

## 五、技术选型与原因

| 技术 | 选型 | 原因 |
|---|---|---|
| 语言 | Java 17 LTS | Records/Sealed Classes 提升代码表达力；长期支持 |
| 后端框架 | Spring Boot 3.2 | 生态成熟、Security/WS/Actuator 一站式 |
| 前端框架 | Vue 3 + TypeScript + Vite | Composition API 更好的代码组织；TS 类型安全；Vite 极快开发体验 |
| 数据库 | PostgreSQL 16 | NUMERIC 精度、窗口函数、部分索引、JSONB、声明式分区，金融系统优选 |
| L1 缓存 | Caffeine | JVM 本地缓存，亚微秒级访问，行情热数据命中率 > 80% |
| L2 缓存 | Redis 7 Cluster (Lettuce) | 分布式缓存+Pub/Sub行情扇出+限流+JWT黑名单+幂等键 |
| 消息队列 | RabbitMQ 3.13 | 可靠ACK、DLQ机制完善、延迟低于Kafka,适合交易消息 |
| ORM | MyBatis-Plus 3.5 | SQL控制精细，金融查询需精确控制SQL |
| 安全 | Spring Security 6 + JWT | 前后端分离无状态鉴权 + Redis黑名单兼顾登出 |
| 图表 | ECharts 5 | 免费开源、中文文档好、K线能力足够 |
| 实时推送 | WebSocket (STOMP over SockJS) | 双向通信、Spring原生集成、按topic订阅、背压控制 |
| 接口文档 | SpringDoc OpenAPI 3 | 自动生成Swagger UI，开发效率高 |
| 负载均衡 | Nginx | upstream 健康检查、limit_req 限流、ip_hash WS粘性会话 |
| 构建 | Maven 3.9+ / pnpm 8+ | Maven稳定可靠；pnpm高效磁盘占用低 |
| CI/CD | GitHub Actions | 零运维、与GitHub深度集成 |
| 容器 | Docker + docker-compose | 一键启动本地开发环境，学习与联调方便 |
| 日志 | SLF4J + Logback → JSON → Loki | 结构化日志，含traceId、userId |
| 监控 | Micrometer + Prometheus + Grafana | 业界标准，指标→采集→可视化→告警 |
| 链路追踪 | Micrometer Tracing → Tempo | 全链路 traceId 贯穿请求生命周期 |
| 代码工具 | Lombok, MapStruct 1.5, Spotless | 减少样板代码、类型安全转换、格式统一 |
| 数据库迁移 | Flyway 10 | 版本化迁移，团队协作有序 |
| 测试 | JUnit5, Mockito, Testcontainers, REST Assured | 分层测试覆盖全链路 |
| 压测 | k6 | 脚本化(JS)更现代、CI友好、支持WS压测 |
| E2E | Playwright | 多浏览器支持、更快 |

---

## 六、系统架构

### 6.1 高并发总体架构图

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                              用户浏览器 / 移动端                              │
│         Vue 3 + TypeScript + Vite + ECharts + WebSocket Client              │
└──────────────────┬─────────────────────────┬─────────────────────────────────┘
                   │ HTTP/REST                │ WS/STOMP
                   ▼                          ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                  Nginx (反向代理 + 负载均衡 + 限流 + 静态资源)                 │
│    limit_req_zone $binary_remote_addr 10m rate=100r/s                       │
│    WebSocket sticky: ip_hash                                                │
└──────────┬──────────────────────┬────────────────────────────────────────────┘
           │                      │
     ┌─────┴──────┐         ┌────┴───────┐
     ▼            ▼         ▼            ▼
┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐
│ App-1   │ │ App-2   │ │ App-3   │ │ App-N   │    ← Spring Boot 水平扩展
└────┬────┘ └────┬────┘ └────┬────┘ └────┬────┘
     │           │           │           │
     ├───────────┴───────────┴───────────┘
     │
     ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│  多级缓存: L1 Caffeine (JVM) + L2 Redis Cluster (3主3从)                     │
│  Pub/Sub: 行情广播 / 缓存失效通知                                             │
└──────────────────────────────────┬───────────────────────────────────────────┘
     ┌─────────────────────────────┼─────────────────────────────┐
     ▼                             ▼                             ▼
┌──────────────┐         ┌──────────────┐              ┌──────────────┐
│ PostgreSQL   │         │  RabbitMQ    │              │ Prometheus + │
│ 主 (写)      │         │  撮合/成交   │              │ Grafana +    │
│ ├→ 从1 (读)  │         │  通知/邮件   │              │ Loki + Tempo │
│ └→ 从2 (读)  │         │  DLQ 死信    │              └──────────────┘
└──────────────┘         └──────────────┘
```

### 6.2 分层架构（DDD-lite 四层）

```
┌──────────────────────────────────────────────────────┐
│  Controller 层（接口适配）                              │
│  - REST API 端点 + 参数校验(@Valid)                    │
│  - DTO/VO 转换 (MapStruct)                            │
│  - 统一 Result<T> 封装                                 │
├──────────────────────────────────────────────────────┤
│  Application 层（用例编排）                             │
│  - 事务边界 (@Transactional)                           │
│  - 编排 domain service + infrastructure               │
│  - Command/Query 对象                                 │
│  - 发布领域事件 (MQ)                                   │
├──────────────────────────────────────────────────────┤
│  Domain 层（核心业务逻辑）  ← 不依赖任何框架            │
│  - Entity（纯POJO）                                    │
│  - Domain Service（业务规则）                          │
│  - Value Object（不可变）                              │
│  - Repository 接口（仅定义）                           │
├──────────────────────────────────────────────────────┤
│  Infrastructure 层（技术实现）                          │
│  - Repository 实现 (MyBatis-Plus Mapper)              │
│  - Gateway (Redis/MQ/HTTP 外部调用)                   │
│  - Adapter (行情Provider适配)                         │
│  - 多级缓存 (Caffeine + Redis)                        │
│  - 读写分离路由 (AbstractRoutingDataSource)            │
│  - Converter (DO ↔ Entity)                            │
└──────────────────────────────────────────────────────┘
```

---

## 七、模块拆分

### 7.1 模块清单

| 模块 | 包路径 | 职责 |
|---|---|---|
| **common** | `com.lzbsdsg.stocksimulation.common` | 统一响应Result、错误码ErrorCode、BizException、全局异常处理、多级缓存抽象、工具类 |
| **config** | `com.lzbsdsg.stocksimulation.config` | SecurityConfig、WebSocketConfig、RedisConfig、CaffeineConfig、DataSourceRoutingConfig、RabbitMQConfig、SwaggerConfig、CorsConfig、AsyncConfig、TradeRuleConfig |
| **auth** | `com.lzbsdsg.stocksimulation.auth` | 注册/登录(密码+OTP)/JWT签发刷新登出/找回密码 |
| **user** | `com.lzbsdsg.stocksimulation.user` | 用户信息、资金账户(Account) CRUD、冻结/解冻 |
| **market** | `com.lzbsdsg.stocksimulation.market` | 行情Provider抽象(Sina/Tencent/Mock)、多级缓存、Pub/Sub扇出推送、股票搜索 |
| **trade** | `com.lzbsdsg.stocksimulation.trade` | 委托下单、撮合成交(并发Consumer)、撤单、手续费计算、幂等 |
| **portfolio** | `com.lzbsdsg.stocksimulation.portfolio` | 持仓管理、资金流水、每日资产快照(分批处理)、收益曲线 |
| **watchlist** | `com.lzbsdsg.stocksimulation.watchlist` | 自选股增删改查、排序 |
| **notification** | `com.lzbsdsg.stocksimulation.notification` | WebSocket推送(背压控制)、站内信、MQ消费成交事件 |
| **admin** | `com.lzbsdsg.stocksimulation.admin` | 用户管理、系统配置、数据查询 |

### 7.2 模块依赖关系

```
auth ──→ user（查询/创建用户+账户）
trade ──→ user（冻结/解冻资金）
trade ──→ market（获取行情判断成交, L1→L2→Provider）
trade ──→ portfolio（更新持仓）
portfolio ──→ user（读取账户余额, @ReadOnly从库）
portfolio ──→ market（计算持仓实时市值, L1→L2）
watchlist ──→ market（关联行情推送）
notification ──→ trade（消费成交事件via MQ fanout）
notification ──→ market（定时推送行情via Pub/Sub）
admin ──→ user, trade（管理查询, @ReadOnly从库）
```

**硬性规则**：跨模块调用只通过 Application Service 接口，不直接引用另一模块的 Domain 层。

---

## 八、代码结构与文件结构

### 8.1 后端目录树

```
stock-simulation/
├── pom.xml
├── Dockerfile
├── docker-compose.dev.yml
├── .github/
│   ├── copilot-instructions.md
│   └── workflows/
│       ├── ci.yml
│       └── ci.yml
├── docs/
│   ├── architecture.md
│   ├── api/openapi-draft.yaml
│   └── agent/prompt-templates/  (12个模板)
├── nginx/
│   └── nginx.conf                                 # 含限流+负载均衡+ip_hash
├── prometheus/
│   ├── prometheus.yml
│   └── alert-rules.yml                            # 告警规则
├── grafana/
│   └── dashboards/
│       ├── jvm-dashboard.json
│       ├── http-dashboard.json
│       ├── trade-dashboard.json
│       └── market-dashboard.json
├── k6/
│   ├── trade-load-test.js                         # 下单压测
│   ├── market-load-test.js                        # 行情压测
│   └── websocket-load-test.js                     # WS压测
├── src/main/java/com/lzbsdsg/stocksimulation/
│   ├── StockSimulationApplication.java
│   │
│   ├── common/
│   │   ├── result/
│   │   │   ├── Result.java                    # 统一响应 {code, message, data, traceId, timestamp}
│   │   │   └── PageResult.java                # 分页响应
│   │   ├── exception/
│   │   │   ├── ErrorCode.java                 # 错误码枚举 MODULE_SCENE_REASON
│   │   │   ├── BizException.java              # 业务异常
│   │   │   └── GlobalExceptionHandler.java    # @RestControllerAdvice
│   │   ├── annotation/
│   │   │   ├── RateLimit.java                 # 限流注解
│   │   │   └── ReadOnly.java                  # 从库路由注解
│   │   ├── aspect/
│   │   │   └── RateLimitAspect.java           # 限流AOP (Redis+Lua令牌桶)
│   │   ├── cache/
│   │   │   ├── MultiLevelCache.java           # 多级缓存抽象
│   │   │   ├── MultiLevelCacheManager.java    # Caffeine L1 + Redis L2 编排
│   │   │   └── CacheInvalidateListener.java   # Redis Pub/Sub缓存失效监听
│   │   └── util/
│   │       ├── JsonUtil.java
│   │       ├── BigDecimalUtil.java            # 金额安全运算
│   │       └── TraceIdUtil.java               # traceId 生成
│   │
│   ├── config/
│   │   ├── SecurityConfig.java                # Spring Security 6 配置
│   │   ├── JwtAuthenticationFilter.java       # JWT过滤器
│   │   ├── WebSocketConfig.java               # STOMP over SockJS
│   │   ├── RedisConfig.java                   # Redis Cluster + Pub/Sub
│   │   ├── CaffeineConfig.java                # Caffeine 本地缓存配置
│   │   ├── DataSourceRoutingConfig.java        # 读写分离数据源路由
│   │   ├── RabbitMQConfig.java                # Exchange/Queue/Binding/DLQ
│   │   ├── SwaggerConfig.java                 # SpringDoc OpenAPI
│   │   ├── CorsConfig.java                    # 跨域白名单
│   │   ├── MyBatisPlusConfig.java             # 分页+乐观锁插件
│   │   ├── AsyncConfig.java                   # 异步线程池配置
│   │   └── TradeRuleConfig.java               # 交易规则可配置Bean
│   │
│   ├── auth/
│   │   ├── controller/
│   │   │   └── AuthController.java
│   │   ├── application/
│   │   │   ├── AuthApplicationService.java
│   │   │   ├── command/
│   │   │   │   ├── SendOtpCommand.java
│   │   │   │   ├── RegisterCommand.java
│   │   │   │   ├── LoginCommand.java
│   │   │   │   ├── LoginByOtpCommand.java
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
│   │       │   └── EmailGateway.java          # SMTP发送走MQ异步
│   │       └── converter/
│   │           └── UserConverter.java
│   │
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
│   │
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
│   │   │   │   └── MarketDataFacade.java      # 多级缓存+降级+节流编排
│   │   │   └── repository/
│   │   │       └── StockInfoRepository.java
│   │   └── infrastructure/
│   │       ├── provider/
│   │       │   ├── MarketDataProvider.java
│   │       │   ├── SinaMarketDataAdapter.java
│   │       │   ├── TencentMarketDataAdapter.java
│   │       │   └── MockMarketDataAdapter.java
│   │       ├── gateway/
│   │       │   └── MarketCacheGateway.java     # 多级缓存实现(Caffeine+Redis)
│   │       ├── ingest/
│   │       │   ├── MarketIngestService.java    # 行情拉取主节点(分布式锁选主)
│   │       │   └── MarketPubSubListener.java   # Pub/Sub订阅 → 更新L1 + 推送WS
│   │       ├── websocket/
│   │       │   ├── MarketWebSocketHandler.java # WS连接管理(注册表+背压)
│   │       │   └── MarketPushScheduler.java
│   │       └── persistence/
│   │           ├── StockInfoMapper.java
│   │           └── StockInfoRepositoryImpl.java
│   │
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
│   │           └── MatchConsumer.java          # prefetch=10, 并发消费者=8
│   │
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
│   │           └── AssetSnapshotScheduler.java # 分批处理(每批500用户, 分布式锁)
│   │
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
│   │
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
│   │
│   └── admin/
│       ├── controller/
│       │   └── AdminController.java
│       └── application/
│           └── AdminApplicationService.java
│
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
│       └── V20260214_001__add_table_partitions.sql     # 流水/快照/成交按月分区
│
└── src/test/java/com/lzbsdsg/stocksimulation/
    ├── auth/
    │   ├── domain/service/
    │   │   ├── OtpDomainServiceTest.java
    │   │   └── PasswordDomainServiceTest.java
    │   ├── application/
    │   │   └── AuthApplicationServiceTest.java
    │   └── controller/
    │       └── AuthControllerApiTest.java
    ├── user/
    │   ├── domain/service/
    │   │   └── AccountDomainServiceTest.java
    │   └── infrastructure/
    │       └── AccountRepositoryIntegrationTest.java
    ├── market/
    │   ├── domain/service/
    │   │   └── MarketDataFacadeTest.java
    │   └── infrastructure/
    │       └── SinaMarketDataAdapterTest.java
    ├── trade/
    │   ├── domain/service/
    │   │   ├── OrderDomainServiceTest.java
    │   │   ├── MatchEngineTest.java
    │   │   └── FeeCalculatorTest.java
    │   ├── application/
    │   │   └── TradeApplicationServiceTest.java
    │   └── controller/
    │       └── OrderControllerApiTest.java
    ├── portfolio/
    │   └── domain/service/
    │       ├── PositionDomainServiceTest.java
    │       └── AssetSnapshotServiceTest.java
    ├── integration/
    │   ├── TradeFullFlowIntegrationTest.java
    │   └── AuthIntegrationTest.java
    └── performance/
        └── TradeConcurrencyTest.java              # 并发下单压力测试
```

### 8.2 前端目录树

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
├── playwright.config.ts
├── public/
│   └── favicon.ico
├── src/
│   ├── main.ts
│   ├── App.vue
│   ├── api/
│   │   ├── request.ts               # Axios实例+拦截器(401 refresh+重试)
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
│   │   ├── useWebSocket.ts           # WS连接管理+自动重连(指数退避1s/2s/4s/8s/30s)+背压检测
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
│   │   ├── market.ts                 # useMarketStore (WS+断线重连)
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
│   │   ├── stores/market.spec.ts
│   │   └── utils/format.spec.ts
│   └── e2e/
│       └── trade-flow.spec.ts
└── Dockerfile
```

---

## 九、数据库设计

### 9.1 ER 关系图

```
t_user 1──1 t_user_account
t_user 1──N t_user_login_log
t_user 1──N t_trade_order
t_trade_order 1──0..1 t_trade_order_archive (按保留策略归档)
t_user 1──N t_portfolio_position
t_user 1──N t_portfolio_fund_flow       (按月分区)
t_user 1──N t_portfolio_asset_snapshot  (按月分区)
t_user 1──N t_watchlist_item
t_user 1──N t_notification_message
t_trade_order 1──N t_trade_deal         (按月分区)
t_portfolio_position N──1 t_market_stock_info
t_watchlist_item N──1 t_market_stock_info
```

### 9.2 建表 SQL

> 完整建表 SQL 见 Flyway 迁移文件 `src/main/resources/db/migration/`
> 以下仅列出表结构概要（与 v1.0 一致，新增分区和连接池说明）

#### 分区策略

```sql
-- 资金流水按月分区
CREATE TABLE t_portfolio_fund_flow (
    id               BIGSERIAL,
    user_id          BIGINT        NOT NULL,
    flow_type        VARCHAR(20)   NOT NULL,
    amount           NUMERIC(18,2) NOT NULL,
    balance_after    NUMERIC(18,2) NOT NULL,
    description      VARCHAR(500),
    related_order_id BIGINT,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

-- 资产快照按月分区
CREATE TABLE t_portfolio_asset_snapshot (
    ...
) PARTITION BY RANGE (snapshot_date);

-- 成交记录按月分区
CREATE TABLE t_trade_deal (
    ...
) PARTITION BY RANGE (traded_at);
```

#### 连接池配置

| 参数 | 主库 | 从库 |
|---|---|---|
| `maximumPoolSize` | 30 | 50 |
| `minimumIdle` | 10 | 20 |
| `connectionTimeout` | 3000ms | 3000ms |
| `idleTimeout` | 600000ms | 600000ms |
| `maxLifetime` | 1800000ms | 1800000ms |
| `leakDetectionThreshold` | 30000ms | 30000ms |

---

## 十、接口设计

> 完整接口设计见 `docs/api/openapi-draft.yaml`（22+ 个 API + WebSocket）

### 10.1 接口汇总

| # | Method | Path | 鉴权 | 限流 | 说明 |
|---|---|---|---|---|---|
| 1 | POST | /api/v1/auth/otp/send | 无 | 1/60s/email, 20/h/ip | 发送邮箱验证码 |
| 2 | POST | /api/v1/auth/register | 无 | — | 邮箱验证码注册 |
| 3 | POST | /api/v1/auth/login | 无 | — | 密码登录 |
| 4 | POST | /api/v1/auth/login/otp | 无 | — | OTP登录 |
| 5 | POST | /api/v1/auth/refresh | 无* | — | 刷新Token |
| 6 | POST | /api/v1/auth/logout | Bearer | — | 登出 |
| 7 | POST | /api/v1/auth/forgot-password | 无 | — | 忘记密码(发OTP) |
| 8 | POST | /api/v1/auth/reset-password | 无 | — | 重置密码(验OTP) |
| 9 | GET | /api/v1/market/quote/{code} | Bearer | 60/min | 单只行情(多级缓存) |
| 10 | GET | /api/v1/market/quotes | Bearer | 60/min | 批量行情(多级缓存) |
| 11 | GET | /api/v1/market/kline/{code} | Bearer | 60/min | K线数据 |
| 12 | GET | /api/v1/market/search | Bearer | 60/min | 搜索股票 |
| 13 | POST | /api/v1/trade/orders | Bearer | 10/min | 委托下单 |
| 14 | DELETE | /api/v1/trade/orders/{id} | Bearer | 10/min | 撤单 |
| 15 | GET | /api/v1/trade/orders | Bearer | 100/min | 委托列表(@ReadOnly从库) |
| 16 | GET | /api/v1/trade/trades | Bearer | 100/min | 成交记录(@ReadOnly从库) |
| 17 | GET | /api/v1/portfolio/overview | Bearer | 100/min | 资产总览 |
| 18 | GET | /api/v1/portfolio/positions | Bearer | 100/min | 持仓列表 |
| 19 | GET | /api/v1/portfolio/fund-flows | Bearer | 100/min | 资金流水(@ReadOnly从库) |
| 20 | GET | /api/v1/portfolio/equity-curve | Bearer | 100/min | 收益曲线(@ReadOnly从库) |
| 21 | GET | /api/v1/watchlist | Bearer | 100/min | 自选股列表 |
| 22 | POST | /api/v1/watchlist | Bearer | 100/min | 添加自选 |
| 23 | DELETE | /api/v1/watchlist/{code} | Bearer | 100/min | 删除自选 |
| 24 | PUT | /api/v1/watchlist/sort | Bearer | 100/min | 自选排序 |
| 25 | GET | /api/v1/notifications | Bearer | 100/min | 消息列表 |
| 26 | PUT | /api/v1/notifications/{id}/read | Bearer | 100/min | 标记已读 |
| 27 | GET | /api/v1/user/me | Bearer | 100/min | 获取当前用户资料 |
| 28 | PUT | /api/v1/user/me | Bearer | 60/min | 修改当前用户资料（昵称） |
| 29 | PUT | /api/v1/user/password | Bearer | 30/min | 修改密码 |
| WS | — | /ws/market | Bearer | 10000连接/实例 | WebSocket行情推送(背压) |

---

## 十一、行情接入抽象层设计

### 11.1 Provider 接口

```java
public interface MarketDataProvider {
    QuoteSnapshot getQuote(String stockCode);
    List<KLinePoint> getKLine(String stockCode, KLinePeriod period, LocalDate from, LocalDate to);
    List<QuoteSnapshot> batchGetQuotes(List<String> stockCodes);
    boolean isAvailable();
}
```

### 11.2 多级缓存编排层 MarketDataFacade

```
getQuote(stockCode):
  1. L1 Caffeine 检查 → key="quote:{code}", TTL=3s, max=5000
     命中(>80%期望) → 直接返回 (<1ms)
  2. L2 Redis 检查 → key="market:quote:{code}", TTL=5s+random(0,500ms)
     命中 → 回填L1 → 返回 (<5ms)
  3. 节流检查 → key="market:throttle:{code}", 3s内重复请求排队等L2
  4. 分布式锁回源 → Redis SETNX "market:load:{code}" TTL=3s → 单实例回源
     调用 primaryProvider.getQuote(code)
     成功 → 写L1+L2 → 返回
  5. primaryProvider失败 → 断路器状态检查 → 备Provider
     成功 → 写L1+L2 → 返回
  6. 全部失败 → 读L2 stale缓存(已过期但存在) → 返回(标记stale)
  7. 无缓存 → 抛出 MarketDataUnavailableException
```

### 11.3 行情推送扇出

```
MarketIngestService (分布式锁选主, 单实例拉取)
  → 写Redis L2
  → Redis Pub/Sub channel: market:quote:broadcast
  → 所有App实例订阅 → 更新L1 Caffeine → 推送各自管理的WS连接
```

### 11.4 降级状态机

```
CLOSED (正常) ──连续失败3次──→ OPEN (断路30s) ──30s后──→ HALF_OPEN (探测1次)
                                      ↑                        │
                                      │                  成功 → CLOSED
                                      └── 失败 ─────────── OPEN
```

---

## 十二、安全设计

### 12.1 认证体系

```
                         ┌─── 密码登录 (BCrypt验证, Caffeine缓存锁定状态)
    用户 ──→ AuthController ├─── OTP登录 (Redis验证码)
                         └─── Token刷新 (Refresh Token Rotation)
                                │
                         ┌──────▼──────┐
                         │ JWT Provider │
                         │ Access 30min │
                         │ Refresh 7d   │
                         └──────┬──────┘
                                │
                         ┌──────▼──────┐
                         │ Redis 黑名单 │ (登出/Rotation作废)
                         └─────────────┘
```

### 12.2 多层限流

| 层次 | 限流规则 | 实现 |
|---|---|---|
| Nginx 全局 | 单IP 100 req/s | `limit_req_zone` |
| 应用全局 | 单用户 100 req/min | Redis + Lua 令牌桶 |
| 交易接口 | 单用户 10 req/min | @RateLimit 注解 |
| 行情接口 | 单用户 60 req/min | @RateLimit 注解 |
| OTP 发送 | 1/60s/email + 20/h/ip | Redis SETNX |
| WebSocket | 10000 连接/实例 | 连接注册表计数 |

---

## 十三、监控日志与告警

### 13.1 日志规范

- 格式：JSON 结构化（logback-spring.xml + LogstashEncoder → Loki）
- 字段：timestamp, level, logger, message, traceId, userId, method, path
- 级别：ERROR(异常) / WARN(业务告警) / INFO(关键操作) / DEBUG(开发)
- 开发环境：INFO 级别，按需开启 SQL 日志

### 13.2 关键业务指标（Prometheus）

| 指标名 | 类型 | 说明 |
|---|---|---|
| `trade_order_created_total` | Counter | 委托创建总数 |
| `trade_order_filled_total` | Counter | 成交总数 |
| `trade_match_duration_seconds` | Histogram | 撮合耗时分布 |
| `market_quote_cache_hit_total{level=L1/L2}` | Counter | 缓存命中(L1/L2分别计数) |
| `market_quote_cache_miss_total` | Counter | 缓存穿透 |
| `ws_active_connections` | Gauge | 活跃WS连接数 |
| `ws_push_duration_seconds` | Histogram | WS推送延迟 |
| `db_pool_active_connections{source=master/slave}` | Gauge | 主/从库活跃连接 |
| `mq_queue_depth` | Gauge | MQ队列深度 |
| `caffeine_hit_rate` | Gauge | L1缓存命中率 |
| `redis_hit_rate` | Gauge | L2缓存命中率 |

### 13.3 告警规则

| # | 条件 | 级别 |
|---|---|---|
| 1 | HTTP 5xx rate > 1% for 5min | Critical |
| 2 | API P99 > 500ms for 5min | Warning |
| 3 | JVM heap > 80% for 10min | Warning |
| 4 | DB connection pool exhausted | Critical |
| 5 | MQ queue depth > 1000 | Warning |
| 6 | 撮合延迟 P99 > 1s | Warning |
| 7 | L1 缓存命中率 < 60% | Warning |
| 8 | WS 连接数 > 8000/instance | Warning (扩容提醒) |
| 9 | PG 复制延迟 > 1s | Warning |
| 10 | 登录失败率 > 30% for 5min | Warning (安全告警) |

---

## 十四、容量规划与扩展路径

### 14.1 容量估算

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

### 14.2 水平扩展路径

| 阶段 | 用户规模 | 部署方式 | 实例数 |
|---|---|---|---|
| MVP | < 1 万 | 单机 (2C4G) | 1 App + 1 PG + 1 Redis |
| 成长期 | 1~10 万 | 双机 + 读写分离 | 2 App + PG主从 + 1 Redis |
| 规模期 | 10~100 万 | 多实例 + Redis Cluster | 4+ App + PG主2从 + Redis 3主3从 + MQ集群 |
| 超大规模 | 100 万+ | 微服务拆分 | 按模块独立部署 + K8s HPA |

---

## 十五、性能压测标准

### 15.1 k6 压测场景

| 场景 | 并发 | 持续 | 通过标准 |
|---|---|---|---|
| 行情查询 | 500 VU | 5min | P95 < 50ms, P99 < 100ms, 错误率 < 0.1% |
| 下单接口 | 200 VU | 5min | P95 < 100ms, P99 < 200ms, 错误率 < 0.1% |
| 混合场景 | 1000 VU | 10min | P99 < 300ms, 错误率 < 0.5% |
| WS 连接 | 10000 连接 | 5min | 推送延迟 P99 < 500ms |
| 登录 | 100 VU | 3min | P99 < 300ms |

### 15.2 CI 集成

- 每次 PR 合入 develop 运行轻量压测（50 VU / 1min）
- 每周定时运行完整压测（上表标准）
- 压测结果写入 Grafana 长期趋势面板

---

## 十六、配置化能力

| 配置项 | 存储位置 | 缓存策略 | 说明 |
|---|---|---|---|
| 交易时间 | t_sys_config | Caffeine TTL=10min | 上午/下午开收盘时间 |
| 涨跌停比例 | t_sys_config | Caffeine TTL=10min | 普通/ST/科创板+创业板 |
| 最小交易单位 | t_sys_config | Caffeine TTL=10min | 默认100股 |
| 手续费费率 | t_trade_fee_config | Caffeine TTL=10min | 佣金/印花税/过户费 |
| 初始资金范围 | t_sys_config | Caffeine TTL=10min | 最低/最高 |
| 自选股上限 | t_sys_config | Caffeine TTL=10min | 默认50只 |
| 行情Provider | application.yml | — | 主源/备源/Mock切换 |
| JWT过期时间 | application.yml | — | access/refresh TTL |
| 限流阈值 | application.yml | — | 各类接口限流配置 |
| DB连接池 | application.yml | — | 主库/从库连接池参数 |
| Caffeine参数 | application.yml | — | maxSize/TTL/refreshAfterWrite |

---

## 十七、未来扩展点

| 扩展方向 | 说明 | 优先级 |
|---|---|---|
| 策略回测 | 用户编写策略脚本，回测历史K线 | P3 |
| 多数据源 | 接入东方财富/同花顺等更多 Provider | P2 |
| 多市场 | 港股/美股模拟 | P3 |
| 排行榜 | 按收益率/胜率/夏普比率排名（Redis ZSet） | P2 |
| 行情提醒 | 价格/涨跌幅触发推送通知 | P2 |
| 市价委托 | 支持市价单（按最优价即时成交） | P1 |
| 部分成交 | 支持订单拆分多次成交 | P2 |
| API 开放 | 提供 REST/WebSocket API 供第三方接入 | P3 |
| 移动端 | 小程序 / React Native App | P3 |
| K8s HPA | 基于 CPU/WS 连接数自动扩缩容 | P2 |
