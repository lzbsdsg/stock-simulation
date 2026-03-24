# Iteration 5 交付说明（行情模块 Pub/Sub 扇出 + 降级）

## 1. 迭代目标

基于 docs/doc-D-dev-roadmap.md 的 Iteration 5 要求，完成以下能力：

- 行情拉取主节点选举（Redis 分布式锁）
- 行情 Pub/Sub 广播与多实例 L1 扇出更新
- Provider 断路器状态机（CLOSED / OPEN / HALF_OPEN）
- batchGetQuotes 优化（逐只 L1/L2 检查，仅 MISS 回源）
- 配套测试与交付验收标准

## 2. 完成任务清单

### 2.1 Infrastructure / Ingest

- 完成 MarketIngestService：
  - 选主锁 key: market:ingest:leader
  - 锁 TTL: 10s
  - 拉取频率: 3s
  - 续期频率: 3s
  - 拉取后写缓存并发布 Redis Pub/Sub channel: market:quote:broadcast

- 完成 MarketPubSubListener：
  - 订阅 market:quote:broadcast
  - 反序列化消息为 QuoteSnapshot
  - 更新本地 L1（Caffeine quote region）
  - 触发 WebSocket 推送（按股票 topic）
  - 序列化异常容错（日志告警并忽略）

- 完成 RedisConfig 广播监听接入：
  - 在 RedisMessageListenerContainer 注册 MarketPubSubListener 到 market:quote:broadcast

### 2.2 降级与批量能力

- 新增 ProviderCircuitBreaker：
  - CLOSED 连续失败 3 次 -> OPEN
  - OPEN 30s 后 -> HALF_OPEN
  - HALF_OPEN 探测成功 -> CLOSED
  - HALF_OPEN 探测失败 -> OPEN

- 完成 MarketDataFacade 断路器接入：
  - 单只行情链路接入断路器
  - 批量行情链路接入断路器
  - fallback 计数器指标 market.provider.fallback.total

- 优化 MarketDataFacade.batchGetQuotes：
  - 先批量检查 L1/L2
  - 仅对 MISS 的股票调用 Provider.batchGetQuotes
  - MISS 失败时按顺序尝试 stale，再执行空值缓存

### 2.3 文件变更清单

- src/main/java/com/lzbsdsg/stocksimulation/market/infrastructure/ingest/MarketIngestService.java
- src/main/java/com/lzbsdsg/stocksimulation/market/infrastructure/ingest/MarketPubSubListener.java
- src/main/java/com/lzbsdsg/stocksimulation/market/infrastructure/resilience/ProviderCircuitBreaker.java
- src/main/java/com/lzbsdsg/stocksimulation/market/domain/service/MarketDataFacade.java
- src/main/java/com/lzbsdsg/stocksimulation/config/RedisConfig.java
- src/test/java/com/lzbsdsg/stocksimulation/market/infrastructure/ingest/MarketIngestServiceTest.java
- src/test/java/com/lzbsdsg/stocksimulation/market/infrastructure/ingest/MarketPubSubListenerTest.java
- src/test/java/com/lzbsdsg/stocksimulation/market/infrastructure/resilience/ProviderCircuitBreakerTest.java
- src/test/java/com/lzbsdsg/stocksimulation/market/domain/service/MarketDataFacadeTest.java

## 3. 测试命令与结果

### 3.1 本次执行命令（已执行）

在项目根目录执行：

```cmd
cd /d d:\StockSimulation\stock-simulation
mvnw.cmd "-Dtest=MarketDataFacadeTest,ProviderCircuitBreakerTest,MarketPubSubListenerTest,MarketIngestServiceTest" test
mvnw.cmd "-Dtest=MarketControllerApiTest,MarketCacheGatewayIntegrationTest,SinaMarketDataAdapterTest,MockMarketDataAdapterTest" test
```

### 3.2 本次执行结果

- 第一组（迭代5新增与改造相关）：22 通过，0 失败
- 第二组（接口与网关回归）：16 通过，0 失败
- 合计：38 通过，0 失败

## 4. 接口与功能验收标准（不止测试类通过）

以下标准需全部满足，方可判定 Iteration 5 通过：

1. 单实例功能正确
- /api/v1/market/quote/{stockCode} 正常返回行情
- /api/v1/market/quotes 批量返回正常
- X-Cache-Status 可观测（HIT-L1 / HIT-L2 / MISS / STALE）

2. 批量优化生效
- 全部命中缓存时，不触发 Provider.batchGetQuotes
- 部分命中时，仅对 MISS 集合回源

3. 多实例选主与扇出
- 同时启动 2 个应用实例，任意时刻仅 1 个实例持有 market:ingest:leader
- 选主实例拉取后，其他实例在短时间内（目标 <100ms）通过 Pub/Sub 更新本地 L1

4. 降级链路正确
- 主 Provider 连续失败 3 次进入 OPEN
- OPEN 30s 后进入 HALF_OPEN 探测
- 探测成功恢复 CLOSED；失败重新 OPEN
- 主源不可用时自动尝试备源/stale，且 market.provider.fallback.total 增长

5. 异常容错
- Pub/Sub 消息反序列化失败时不影响其他消息处理
- 不存在股票代码命中空值缓存，不持续穿透 Provider

## 5. 验收操作脚本（建议）

### 5.1 双实例选主检查

```cmd
:: 实例A
set SERVER_PORT=8080 && mvnw.cmd spring-boot:run

:: 实例B（新终端）
set SERVER_PORT=8081 && mvnw.cmd spring-boot:run
```

```cmd
：使用 docker compose.dev 的 Redis 容器内 redis-cli（无需本机安装）
docker compose -f docker-compose.dev.yml exec redis-node-1 redis-cli -p 7000 -a <REDIS_PASSWORD> GET market:ingest:leader


```

预期：存在且仅一个 token；实例故障后约 10s 可被其他实例接管。

### 5.2 接口可用性检查

```http
GET /api/v1/market/quote/sh600519
GET /api/v1/market/quotes?codes=sh600519&codes=sz000001
GET /api/v1/market/search?keyword=茅台
```

预期：HTTP 200，返回结构符合 Result<T>；可观察缓存状态头。

### 5.3 降级检查

- 通过测试桩或网络隔离让主 Provider 连续失败，观察断路器状态迁移与 fallback 指标。
- 全部 Provider 失败且存在 stale 时，返回 stale 且 X-Cache-Status=STALE。

### 5.4 本轮联调验收记录（2026-03-24）

1. 双实例在线与单 Leader 稳定
- 观测到 8080 与 8081 同时监听。
- 连续读取 `market:ingest:leader`，Token 稳定为同一值：`7c78b808-5534-446e-9b49-b6c7b72ca1e5`。

2. 故障切换验证
- 停掉其中一个实例后，Leader Token 从 `f45e55a4-ef57-47c9-b64e-1c6658eded11` 切换为 `7c78b808-5534-446e-9b49-b6c7b72ca1e5`。
- 切换后连续多次读取保持稳定，确认接管成功。

3. 关键测试回归
- 执行与 Iteration 5 相关的 6 个测试文件，结果：26 通过，0 失败。

4. 当前状态
- 选主、续期、故障接管：通过。
- 断路器与批量回源优化：通过（测试通过）。
- Pub/Sub 扇出 <100ms：需补充量化采样证据（见 5.5）。

### 5.5 Pub/Sub 扇出时延采样（补证据）

目的：量化“选主实例拉取后，非选主实例 L1 更新延迟 <100ms”。

建议做法：
- 在 `MarketIngestService` 发布广播前打印毫秒时间戳与 stockCode。
- 在 `MarketPubSubListener` 收到广播并写入 L1 后打印毫秒时间戳与 stockCode。
- 两端日志按同一 stockCode 对齐，计算 `listener_ts - publish_ts`。
- 采样至少 200 条，统计 p50 / p95 / p99。

启用方式：
- 启动实例时设置环境变量：`MARKET_INGEST_LATENCY_SAMPLE_ENABLED=true`。
- 日志关键字：`market.pubsub.fanout.delay`。

示例统计（CMD）：
- 在 `cmd` 终端中先导出日志，再用 `findstr` 筛选关键行（`market.pubsub.fanout.delay`）。
- 从筛选结果提取 `delayMs=xxx` 后统计分位值（p50/p95/p99）。
- 可直接使用统计脚本：

```cmd
cd /d d:\StockSimulation\stock-simulation
D:\Miniconda3_310\envs\lab7\python.exe tools\measure_fanout_latency.py logs\app8081-latency.log
```

本次采样结果（2026-03-24）：
- `count=1300`
- `p50=3ms`
- `p95=5ms`
- `p99=9ms`
- `max=41ms`

通过阈值：
- p95 < 100ms，且 p99 无持续性尖峰（偶发尖峰需给出原因）。

## 6. Iteration 5 通过结论

当前代码与测试结果表明，Iteration 5 目标能力已落地：

- 已具备主节点拉取 + Pub/Sub 广播 + 多实例本地缓存扇出
- 已具备断路器状态机降级与 fallback 指标
- 已具备 batchGetQuotes 的“仅 MISS 回源”优化
- 已有单元/集成/API 回归测试证据（历史 38/38 通过；本轮关键回归 26/26 通过）

在联调环境补齐第 5.5 节的时延量化采样并达标后，可判定该模块满足迭代5交付标准并可正常工作。
