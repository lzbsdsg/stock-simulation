# Iteration 8 交付说明（交易模块：MQ 异步撮合 + 成交）

## 1. 迭代目标

基于 `docs/doc-D-dev-roadmap.md` 的 Iteration 8 要求，完成：

- MQ 异步撮合消费（并发消费、幂等跳过）
- 成交事务闭环（订单/成交/持仓/资金/流水）
- 乐观锁冲突重试（指数退避）与重试失败入 DLQ
- 成交事件 `trade.filled` 发布（fanout）
- 收盘结算任务（过期待成交 + T+1 冻结日期修正）
- 历史委托归档任务（主表保留窗口 + 归档表长期保留）

## 2. 本次完成项

### 2.1 撮合与结算主链路

- `TradeApplicationService.matchOrder(orderId)`：
  - 幂等检查：订单不存在或已非待成交状态时跳过
  - 行情获取：走 `MarketDataFacade`
  - 价格条件判断：买入 `order.price >= marketPrice`，卖出 `order.price <= marketPrice`
  - 成交事务：
    - `INSERT Trade`
    - `UPDATE Order(FILLED)`（乐观锁）
    - 买入：`Account.deductFrozen` + 持仓加仓（加权成本）+ `frozen_until=下一交易日`
    - 卖出：持仓减仓（扣冻结）+ `Account.creditBalance`
    - `INSERT FundFlow(TRADE_BUY/TRADE_SELL)`
  - 事务提交后发布 `trade.filled` 事件

### 2.2 MQ 消费、重试、DLQ

- `MatchConsumer`：
  - 使用专用容器工厂：`prefetch=10`, `concurrentConsumers=8`
  - 乐观锁冲突重试：`max=3`，退避 `50ms * 2^(n-1)`
  - 重试耗尽后抛 `AmqpRejectAndDontRequeueException` 进入 DLQ

- `RabbitMQConfig`：
  - `trade.match.queue` 增加死信配置
  - 新增 `trade.match.dlx` + `trade.match.dlq`
  - 新增 `trade.filled.exchange`（fanout）
  - `trade.notification.queue` 绑定 `trade.filled.exchange`

### 2.3 成交通知

- `OrderMessageProducer.sendTradeFilledEvent(...)` 发布成交事件
- `TradePushConsumer` 消费 `TradeFilledEvent` 并调用通知服务
- `NotificationApplicationService` 补齐：
  - 列表查询、单条已读、全部已读、未读数
  - `sendNotification` 落库并尝试 WS 推送 `/user/queue/notification`

### 2.4 收盘结算任务

- 新增 `TradeSettlementScheduler`：
  - 15:00 工作日执行（可配置 cron）
  - Redis 分布式锁防重入
  - 批量过期待成交订单（每批 200）
  - 执行当日买入持仓 `frozen_until` 修正

### 2.5 领域能力补齐

- `MatchEngine` 改为接收“实际手续费金额”
- `PositionDomainService` 增加：
  - 买入成交持仓更新（加权成本 + T+1）
  - 卖出成交持仓更新（扣冻结、减总仓）
  - 下一交易日计算（跳过周末）

### 2.6 历史委托归档（增补）

- 新增 Flyway 迁移：`V20260325_001__create_trade_order_archive_table.sql`
  - 新表 `t_trade_order_archive`，按 `order_id` 唯一存储归档订单
  - 保留原订单关键字段 + `archived_at`
- 新增 `OrderArchiveMapper`：
  - 历史查询：主表 `t_trade_order` + 归档表 `t_trade_order_archive` 合并分页
  - 归档搬迁：批量迁移 `CANCELLED/EXPIRED/REJECTED` 且无成交明细订单
- `OrderRepositoryImpl`：
  - 历史委托查询与计数改为“主表+归档表”统一视图，避免归档后历史查询丢数据
  - 新增 `archiveClosedOrdersWithoutTrades(...)` 归档入口
- `TradeApplicationService`：
  - 新增 `archiveClosedOrders(retainDays, batchSize)` 归档编排
- `TradeSettlementScheduler`：
  - 新增归档调度 `archiveOrders`（默认 cron: `0 30 3 * * *`）
  - Redis 分布式锁防重 + 分批循环执行
  - 可配置：`trade.settlement.archive-retain-days`、`trade.settlement.archive-batch-size`

## 3. 关键变更文件

- `src/main/java/com/lzbsdsg/stocksimulation/trade/application/TradeApplicationService.java`
- `src/main/java/com/lzbsdsg/stocksimulation/trade/infrastructure/mq/MatchConsumer.java`
- `src/main/java/com/lzbsdsg/stocksimulation/trade/infrastructure/mq/OrderMessageProducer.java`
- `src/main/java/com/lzbsdsg/stocksimulation/trade/infrastructure/mq/TradeFilledEvent.java`
- `src/main/java/com/lzbsdsg/stocksimulation/trade/infrastructure/scheduler/TradeSettlementScheduler.java`
- `src/main/java/com/lzbsdsg/stocksimulation/trade/infrastructure/persistence/OrderArchiveMapper.java`
- `src/main/resources/db/migration/V20260325_001__create_trade_order_archive_table.sql`
- `src/main/java/com/lzbsdsg/stocksimulation/config/RabbitMQConfig.java`
- `src/main/java/com/lzbsdsg/stocksimulation/notification/application/NotificationApplicationService.java`
- `src/main/java/com/lzbsdsg/stocksimulation/notification/infrastructure/mq/TradePushConsumer.java`
- `src/main/java/com/lzbsdsg/stocksimulation/portfolio/domain/service/PositionDomainService.java`
- `src/main/java/com/lzbsdsg/stocksimulation/portfolio/infrastructure/persistence/PositionMapper.java`

## 4. 测试命令与结果

### 4.1 已执行

```cmd
cd /d d:\StockSimulation\stock-simulation
mvnw.cmd "-Dtest=TradeApplicationServiceTest,TradeSettlementSchedulerTest" test
```

结果：`Tests run: 16, Failures: 0, Errors: 0, Skipped: 0`

### 4.2 Spotless（本次改动文件）

```cmd
cd /d d:\StockSimulation\stock-simulation
mvnw.cmd spotless:check "-DspotlessFiles=src/main/java/com/lzbsdsg/stocksimulation/trade/application/TradeApplicationService.java,src/main/java/com/lzbsdsg/stocksimulation/trade/domain/repository/OrderRepository.java,src/main/java/com/lzbsdsg/stocksimulation/trade/infrastructure/persistence/OrderArchiveMapper.java,src/main/java/com/lzbsdsg/stocksimulation/trade/infrastructure/persistence/OrderRepositoryImpl.java,src/main/java/com/lzbsdsg/stocksimulation/trade/infrastructure/scheduler/TradeSettlementScheduler.java,src/test/java/com/lzbsdsg/stocksimulation/trade/application/TradeApplicationServiceTest.java,src/test/java/com/lzbsdsg/stocksimulation/trade/infrastructure/scheduler/TradeSettlementSchedulerTest.java"
```

结果：通过。

### 4.3 并发撮合与乐观锁重试（补充）

```cmd
cd /d d:\StockSimulation\stock-simulation
mvnw.cmd "-Dtest=MatchConsumerTest" test
```

结果：通过。

说明：
- `MatchConsumerTest.should_retry_when_optimistic_lock_conflict_then_success` 已覆盖“发生乐观锁冲突后自动重试并最终成功”。
- “10线程并发撮合同一用户”作为集成验收项放在 `6.6`（手工并发验收）执行，避免单测中引入不稳定线程调度噪声。

## 5. 验收标准（不只测试通过）

1. 接口与业务链路
- 下单后可被 MQ 消费并成交：订单 `PENDING -> FILLED`
- `GET /api/v1/trade/trades` 可查到成交记录
- 买入成交后持仓冻结（T+1），卖出成交后资金入账

2. 一致性与可靠性
- 并发/冲突下撮合可重试，日志可见重试
- 重试失败消息进入 `trade.match.dlq`
- 成交事件可被通知消费者消费并落库

3. 调度任务
- 收盘任务执行后：待成交订单可过期，持仓冻结日期可修正
- 分布式锁生效（同一日期仅一个实例执行）
- 归档任务执行后：符合条件订单从 `t_trade_order` 迁移到 `t_trade_order_archive`
- `GET /api/v1/trade/orders?scope=history` 在归档后仍可查到迁移订单

4. 稳定性与性能（本迭代）
- 冒烟压测期间无连接拒绝/大面积 5xx
- MQ 消费链路在低并发下稳定处理，不出现异常中断
- 归档批处理不阻塞线上交易接口（独立凌晨 cron + 批量处理）
- 10 线程并发撮合同一用户时，请求整体可完成；冲突请求可通过乐观锁重试成功或幂等跳过，不出现 500

## 6. 具体验收方法（可执行）

### 6.1 启动依赖与服务

```cmd
cd /d d:\StockSimulation\stock-simulation
mvnw.cmd spring-boot:run
```

### 6.2 接口验收（下单 -> 成交 -> 查询）

1. 调用 `POST /api/v1/trade/orders` 下买单（限价满足当前价）
   {
  "clientOrderId": "it7-buy-001",
  "stockCode": "sh600519",
  "side": "BUY",
  "orderType": "LIMIT",
  "price": 1418.88,
  "quantity": 100
}
2. 等待 1~3 秒（MQ 异步撮合）
3. 调用 `GET /api/v1/trade/orders?scope=today&page=1&size=20`
   - 预期订单状态为 `FILLED`
4. 调用 `GET /api/v1/trade/trades?page=1&size=20`
   - 预期出现对应 `orderId` 的成交记录

### 6.3 MQ 与 DLQ 验收

本节采用“不看后端控制台日志”的验收方式。

1. 打开 `http://localhost:15672`，登录 `.env` 中 RabbitMQ 账号（示例：`stock_app`）。
2. 进入 `Queues and Streams`，确认存在：
   - `trade.match.queue`
   - `trade.match.dlq`
   - `trade.notification.queue`
3. 点击 `trade.match.queue`，确认 `Effective policy definition` 包含：
   - `dead-letter-exchange = trade.match.dlx`
   - `dead-letter-routing-key = trade.match.dlq`
4. 正常链路验收（MQ）：
   - 调用一次下单接口（消息进入 `trade.exchange` + `trade.match`）。
   - 预期 `trade.match.queue` 无持续堆积（`Ready` 归零或接近 0），且 `Ack` 增长。
   - 预期 `trade.match.dlq` 不增长。
5. DLQ 验收：
   - 在 `Exchanges -> trade.exchange -> Publish message` 发布坏消息。
   - `Routing key` 填 `trade.match`，`Payload` 示例：`{"bad":"data"}`。
   - 预期消息进入 `trade.match.dlq`，并可在 `Get messages` 中看到 `x-death.reason = rejected`。

可选：CMD + curl 验收

```cmd
set RMQ_USER=stock_app
set RMQ_PASS=<RABBITMQ_DEFAULT_PASS>

REM 查看关键队列
curl -s -u %RMQ_USER%:%RMQ_PASS% "http://localhost:15672/api/queues/%2F/trade.match.queue"
curl -s -u %RMQ_USER%:%RMQ_PASS% "http://localhost:15672/api/queues/%2F/trade.match.dlq"
curl -s -u %RMQ_USER%:%RMQ_PASS% "http://localhost:15672/api/queues/%2F/trade.notification.queue"

REM 发布坏消息触发 DLQ
curl -s -u %RMQ_USER%:%RMQ_PASS% -H "content-type: application/json" ^
  -X POST "http://localhost:15672/api/exchanges/%2F/trade.exchange/publish" ^
  -d "{\"properties\":{\"content_type\":\"application/json\"},\"routing_key\":\"trade.match\",\"payload\":\"{\\\"bad\\\":\\\"data\\\"}\",\"payload_encoding\":\"string\"}"

REM 查看 DLQ 是否增长
curl -s -u %RMQ_USER%:%RMQ_PASS% "http://localhost:15672/api/queues/%2F/trade.match.dlq"
```

若历史环境缺失 DLQ 资源（仅需执行一次，CMD + curl）

```cmd
set RMQ_USER=stock_app
set RMQ_PASS=<RABBITMQ_DEFAULT_PASS>

curl -s -u %RMQ_USER%:%RMQ_PASS% -H "content-type: application/json" ^
  -X PUT "http://localhost:15672/api/queues/%2F/trade.match.dlq" ^
  -d "{\"auto_delete\":false,\"durable\":true,\"arguments\":{}}"

curl -s -u %RMQ_USER%:%RMQ_PASS% -H "content-type: application/json" ^
  -X POST "http://localhost:15672/api/bindings/%2F/e/trade.match.dlx/q/trade.match.dlq" ^
  -d "{\"routing_key\":\"trade.match.dlq\",\"arguments\":{}}"

curl -s -u %RMQ_USER%:%RMQ_PASS% -H "content-type: application/json" ^
  -X PUT "http://localhost:15672/api/policies/%2F/trade-match-dlq-policy" ^
  -d "{\"pattern\":\"^trade\\.match\\.queue$\",\"definition\":{\"dead-letter-exchange\":\"trade.match.dlx\",\"dead-letter-routing-key\":\"trade.match.dlq\"},\"priority\":1,\"apply-to\":\"queues\"}"
```

可选：PowerShell 验收

```powershell
$cred = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("stock_app:<RABBITMQ_DEFAULT_PASS>"))
$h = @{ Authorization = "Basic $cred"; "content-type" = "application/json" }

Invoke-RestMethod -Headers $h -Uri "http://localhost:15672/api/queues" |
  Where-Object { $_.name -in @("trade.match.queue","trade.match.dlq","trade.notification.queue") } |
  Select-Object name,consumers,messages,messages_ready,messages_unacknowledged,effective_policy_definition
```
### 6.4 收盘任务验收

测试环境建议临时设置：

```yaml
trade:
  settlement:
    close-cron: "*/30 * * * * *"
```

观察日志：

- `trade.close.done date=... expiredOrders=... markedPositions=...`

恢复生产 cron 后再提交。

### 6.5 性能冒烟验收（迭代8）

```cmd
cd /d d:\StockSimulation\stock-simulation
k6 run -e BASE_URL=http://localhost:8080 -e TOKEN=<accessToken> -e STOCK_CODE=sh600519 -e ORDER_PRICE=1618.88 -e ORDER_QUANTITY=100 -e VUS=5 -e DURATION=1m -e ACCEPT_429=true k6/trade-load-test.js
```

通过标准：

- 无 `connectex actively refused`
- `hard_failure_rate < 1%`
- 下单/查单请求可持续返回（触发 429 视为限流生效，不计功能失败）

### 6.6 并发撮合验收（10线程，同一用户）

1. 准备同一用户的 10 条可成交订单（建议不同 `clientOrderId`，可同一 `stockCode`）。
2. 使用并发工具同时触发撮合消费（或快速连续下单后由 MQ 并发消费）：

```cmd
cd /d d:\StockSimulation\stock-simulation
k6 run -e BASE_URL=http://localhost:8080 -e TOKEN=<accessToken> -e STOCK_CODE=sh600519 -e ORDER_PRICE=1618.88 -e ORDER_QUANTITY=100 -e VUS=10 -e DURATION=20s -e ACCEPT_429=true k6/trade-load-test.js
```

3. 在 RabbitMQ UI 观察 `trade.match.queue` 无长期堆积，消费者持续 ACK。
4. 调用：
   - `GET /api/v1/trade/orders?scope=today&page=1&size=50`
   - `GET /api/v1/trade/trades?page=1&size=50`

通过标准：
- 无 500 / 无连接拒绝；
- 订单最终状态稳定（`FILLED/CANCELLED/EXPIRED` 中之一，不出现非法中间态）；
- 可看到成交结果，且资金/持仓无负数、无越界；
- 发生冲突时，日志可见重试后成功或幂等跳过（不重复成交）。

### 6.7 归档验收（新增）

1. 启动服务后，准备 1 条可归档样本订单（`status=CANCELLED/EXPIRED/REJECTED` 且无成交记录）。
2. 将该订单 `updated_at` 调整到保留窗口外（例如 8 天前）。
3. 临时将归档 cron 调整为快速触发：

```yaml
trade:
  settlement:
    archive-cron: "*/30 * * * * *"
    archive-retain-days: 7
    archive-batch-size: 100
```

4. 观察日志：`trade.archive.done date=... archivedOrders=...`
5. 验证数据库：
   - `t_trade_order` 中样本订单消失
   - `t_trade_order_archive` 中出现同 `order_id` 数据
6. 调用 `GET /api/v1/trade/orders?scope=history&page=1&size=20`，确认可查到该订单。

## 7. Iteration 8 通过结论

Iteration 8 交易模块核心能力已完成并可运行：MQ 异步撮合、成交结算、重试+DLQ、成交事件发布、通知消费、收盘结算与历史委托归档任务均已落地，并提供了可执行的测试与验收方法。
