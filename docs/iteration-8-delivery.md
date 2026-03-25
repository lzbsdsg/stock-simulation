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

## 6. 具体验收方法（可执行）

### 6.1 启动依赖与服务

```cmd
cd /d d:\StockSimulation\stock-simulation
mvnw.cmd spring-boot:run
```

### 6.2 接口验收（下单 -> 成交 -> 查询）

1. 调用 `POST /api/v1/trade/orders` 下买单（限价满足当前价）
2. 等待 1~3 秒（MQ 异步撮合）
3. 调用 `GET /api/v1/trade/orders?scope=today&page=1&size=20`
   - 预期订单状态为 `FILLED`
4. 调用 `GET /api/v1/trade/trades?page=1&size=20`
   - 预期出现对应 `orderId` 的成交记录

### 6.3 MQ 与 DLQ 验收

1. 正常成交后，日志应出现：
   - `trade.match.ok ...`
   - `Sending trade filled event ...`
2. 人为制造乐观锁冲突（测试环境并发触发）后，日志应出现重试退避：
   - `match retry backoff: attempt=...`
3. 连续冲突超过 3 次后，消息应进入 `trade.match.dlq`

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
k6 run -e BASE_URL=http://localhost:8080 -e TOKEN=<accessToken> -e STOCK_CODE=sh600519 -e ORDER_PRICE=1688.88 -e ORDER_QUANTITY=100 -e VUS=5 -e DURATION=1m -e ACCEPT_429=true k6/trade-load-test.js
```

通过标准：

- 无 `connectex actively refused`
- `hard_failure_rate < 1%`
- 下单/查单请求可持续返回（触发 429 视为限流生效，不计功能失败）

### 6.6 归档验收（新增）

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
