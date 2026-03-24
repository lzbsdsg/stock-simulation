# Iteration 7 交付说明（交易模块：下单/撤单）

## 1. 迭代目标

基于 `docs/doc-D-dev-roadmap.md` 的 Iteration 7 要求，完成交易模块在“未进入撮合前”的核心能力：

- 下单（买入/卖出）主链路
- 撤单主链路
- 幂等校验（`clientOrderId`）
- 短事务与事务耗时日志
- 交易接口限流（10 次/分钟）
- 可执行的接口验收与性能验收

## 2. 完成任务清单

### 2.1 应用服务（TradeApplicationService）

- 完成 `placeOrder`：
  - Redis 幂等拦截（重复 `clientOrderId` 返回 `TRADE_ORDER_DUPLICATE`）
  - 前置校验：交易时间、100 股整数倍、涨跌停区间
  - 买单：冻结资金、落库 `Order(PENDING)`、落库 `FundFlow(FREEZE)`、发送撮合消息
  - 卖单：冻结持仓（`available -> frozen`）、落库 `Order(PENDING)`、发送撮合消息
  - 记录事务耗时日志（`trade.placeOrder.ok/slow`）

- 完成 `cancelOrder`：
  - 校验订单归属与状态可撤销（`PENDING/PARTIAL_FILLED`）
  - CAS 更新订单状态为 `CANCELLED`（`updateWithVersion`）
  - 买单撤单：解冻资金 + `FundFlow(UNFREEZE)`
  - 卖单撤单：解冻持仓（`frozen -> available`）
  - 记录事务耗时日志（`trade.cancelOrder.ok/slow`）

- 完成查询接口编排：
  - `getOrders(scope, page, size)` 支持 `today/history/all`
  - `getTrades(page, size)`
  - 查询方法标注 `@ReadOnly`

### 2.2 控制器契约（OrderController）

- 新增并对齐设计文档路径：
  - `POST /api/v1/trade/orders`
  - `DELETE /api/v1/trade/orders/{orderId}`
  - `GET /api/v1/trade/orders`
  - `GET /api/v1/trade/trades`

- 交易写接口限流：
  - `@RateLimit(limit = 10, window = 60)`

### 2.3 幂等与交易规则

- `IdempotencyGateway` key 前缀调整为 `idempotent:order:`
- `OrderDomainService` 增加可配置交易时段判断重载，并将交易时间判断改为区间端点可用（含开闭市时间点）

### 2.4 测试与压测脚本

- 完成测试类：
  - `OrderDomainServiceTest`
  - `FeeCalculatorTest`
  - `TradeApplicationServiceTest`
  - `OrderControllerApiTest`

- 完成压测脚本：
  - `k6/trade-load-test.js`（下单 + 查询 + 按比例撤单）

## 3. 文件变更清单

### 3.1 主要代码变更

- `src/main/java/com/lzbsdsg/stocksimulation/trade/application/TradeApplicationService.java`
- `src/main/java/com/lzbsdsg/stocksimulation/trade/controller/OrderController.java`
- `src/main/java/com/lzbsdsg/stocksimulation/trade/domain/service/OrderDomainService.java`
- `src/main/java/com/lzbsdsg/stocksimulation/trade/infrastructure/gateway/IdempotencyGateway.java`

### 3.2 测试与脚本

- `src/test/java/com/lzbsdsg/stocksimulation/trade/domain/service/OrderDomainServiceTest.java`
- `src/test/java/com/lzbsdsg/stocksimulation/trade/domain/service/FeeCalculatorTest.java`
- `src/test/java/com/lzbsdsg/stocksimulation/trade/application/TradeApplicationServiceTest.java`
- `src/test/java/com/lzbsdsg/stocksimulation/trade/controller/OrderControllerApiTest.java`
- `k6/trade-load-test.js`

## 4. 测试命令与结果

### 4.1 已执行命令（本轮）

```cmd
cd /d d:\StockSimulation\stock-simulation
mvnw.cmd "-Dtest=OrderDomainServiceTest,FeeCalculatorTest,TradeApplicationServiceTest,OrderControllerApiTest" test
```

执行结果：

- 通过：25
- 失败：0

### 4.2 Spotless 校验（本轮）

已对本次迭代改动文件执行 Spotless 校验：

```cmd
cd /d d:\StockSimulation\stock-simulation
mvnw.cmd spotless:check "-DspotlessFiles=src/main/java/com/lzbsdsg/stocksimulation/trade/application/TradeApplicationService.java,src/main/java/com/lzbsdsg/stocksimulation/trade/controller/OrderController.java,src/main/java/com/lzbsdsg/stocksimulation/trade/domain/service/OrderDomainService.java,src/main/java/com/lzbsdsg/stocksimulation/trade/infrastructure/gateway/IdempotencyGateway.java,src/test/java/com/lzbsdsg/stocksimulation/trade/application/TradeApplicationServiceTest.java,src/test/java/com/lzbsdsg/stocksimulation/trade/controller/OrderControllerApiTest.java,src/test/java/com/lzbsdsg/stocksimulation/trade/domain/service/OrderDomainServiceTest.java,src/test/java/com/lzbsdsg/stocksimulation/trade/domain/service/FeeCalculatorTest.java"
```

执行结果：通过。

说明：仓库存在其它历史文件的格式欠账，`spotless:check` 全仓执行当前仍会因历史文件失败，不属于本次迭代新增问题。

## 5. 验收标准（不只测试类通过）

以下标准需全部满足，才可判定 Iteration 7 通过：

1. 接口可用性
- `POST /api/v1/trade/orders` 可创建 `PENDING` 订单
- 买单下单后可用资金冻结，存在 `FundFlow(FREEZE)`
- `DELETE /api/v1/trade/orders/{id}` 可撤销待成交订单
- 买单撤单后资金解冻，存在 `FundFlow(UNFREEZE)`
- `GET /api/v1/trade/orders`、`GET /api/v1/trade/trades` 分页正常

2. 规则正确性
- 重复 `clientOrderId` 返回 `409`（幂等冲突）
- 非 100 股整数倍下单返回 `400`
- 非交易时间下单返回 `400`
- 卖出持仓不足返回 `400`
- 交易接口超过 10 次/分钟返回 `429` 且带 `X-RateLimit-*` 头

3. 性能与稳定性
- 下单接口压测满足：`P95 < 100ms`、`P99 < 200ms`、`错误率 < 0.1%`
- 服务端可观测到事务耗时日志（`trade.placeOrder.ok/slow`, `trade.cancelOrder.ok/slow`）

## 6. 具体验收方法（可直接执行）

### 6.1 启动服务

```cmd
cd /d d:\StockSimulation\stock-simulation
mvnw.cmd spring-boot:run
```

### 6.2 获取访问令牌

1. 打开 `http://localhost:8080/swagger-ui.html`
2. 调用 `/api/v1/auth/login` 获取 `accessToken`
3. 后续请求使用 `Authorization: Bearer <accessToken>`

### 6.3 下单验收（买单）

```http
POST /api/v1/trade/orders
Content-Type: application/json
Authorization: Bearer <accessToken>

{
  "clientOrderId": "it7-buy-001",
  "stockCode": "sh600519",
  "side": "BUY",
  "orderType": "LIMIT",
  "price": 1688.88,
  "quantity": 100
}
```

预期：

- HTTP 200，`data.status = "PENDING"`
- 返回 `orderId`
- 数据库可看到 `t_trade_order` 新增记录
- 数据库可看到 `t_portfolio_fund_flow` 新增 `FREEZE` 记录

### 6.4 幂等验收

重复发送相同 `clientOrderId`：

- 预期 HTTP 409，错误码 `TRADE_ORDER_DUPLICATE`

### 6.5 撤单验收

```http
DELETE /api/v1/trade/orders/{orderId}
Authorization: Bearer <accessToken>
```

预期：

- HTTP 200
- 订单状态变更为 `CANCELLED`
- 买单场景有 `UNFREEZE` 资金流水

### 6.6 限流验收

对同一用户 1 分钟内连续提交 11 次下单请求：

- 第 11 次开始应返回 HTTP 429
- 响应头存在 `X-RateLimit-Limit` / `X-RateLimit-Remaining` / `X-RateLimit-Reset`

### 6.7 性能验收（k6）

```cmd
cd /d d:\StockSimulation\stock-simulation
k6 run -e BASE_URL=http://localhost:8080 -e TOKEN=<accessToken> -e STOCK_CODE=sh600519 -e ORDER_PRICE=1688.88 -e ORDER_QUANTITY=100 -e VUS=200 -e DURATION=5m k6/trade-load-test.js
```

通过标准：

- `http_req_duration{p(95)} < 100ms`
- `http_req_duration{p(99)} < 200ms`
- `http_req_failed rate < 0.001`

## 7. Iteration 7 通过结论

本次已完成迭代7交易模块核心交付：下单/撤单/幂等/限流/查询/压测脚本与验收闭环，并给出“功能 + 接口 + 性能”的可执行验收方法。  
在当前代码状态下，迭代7新增测试已全部通过，满足该迭代目标的可交付要求。
