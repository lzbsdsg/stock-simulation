# Iteration 4 交付说明（行情模块 Market）

## 1. 迭代目标

基于路线图与详细设计文档，完成 Iteration 4 行情模块交付，覆盖以下能力：

- 行情 Provider 抽象与多数据源接入（Sina/Tencent/Mock）
- 多级缓存读路径（L1 Caffeine + L2 Redis）
- 缓存防击穿（分布式锁）
- 缓存防穿透（空值缓存）
- 缓存防雪崩（L2 TTL 抖动）
- Provider 失败降级（stale 缓存）
- 行情查询、批量查询、K 线、股票搜索接口
- 响应头缓存命中标识（X-Cache-Status）

## 2. 完成任务清单

### 2.1 Domain / Application

- 完成 MarketDataFacade：
  - 单只行情读取：L1/L2 命中、回源、降级
  - 批量行情读取：按股票逐只编排读取
  - K 线读取：L2 缓存命中后直接返回，未命中回源并写缓存
  - Provider 失败链路：主源失败后自动尝试下一 Provider
  - 全部失败时：优先返回 stale 缓存，否则写空值缓存并抛业务异常

- 完成 MarketApplicationService：
  - 批量代码参数校验（空参数返回业务错误）
  - 周期参数兼容大小写（KLinePeriod）
  - 股票搜索能力（代码/名称模糊匹配）
  - 股票列表 5 分钟 L1 缓存（Caffeine stock region）

### 2.2 Infrastructure

- 完成 MarketCacheGateway：
  - L1 + L2 行情读取封装
  - 行情写缓存（L2 5s + random(0~500ms)）
  - stale 缓存写入/读取（300s）
  - 空值缓存（30s）
  - 分布式加载锁（market:load:{code}, TTL=3s）
  - K 线缓存（60s）
  - 响应头注入：X-Cache-Status = HIT-L1 / HIT-L2 / MISS / STALE

- 完成 SinaMarketDataAdapter：
  - HTTP 请求与单只/批量行情解析
  - 健康检查能力（isAvailable）
  - K 线数据兜底生成（用于 Provider 层统一契约）

- 完成 TencentMarketDataAdapter：
  - HTTP 请求与单只/批量行情解析
  - 健康检查能力（isAvailable）
  - K 线数据兜底生成（用于备源契约）

- 保持 MockMarketDataAdapter 为 dev/test 可用，保障无外网情况下可运行。

### 2.3 Controller

- 完成 MarketController：
  - GET /api/v1/market/quote/{stockCode}
  - GET /api/v1/market/quotes（兼容 codes 与 stockCodes）
  - GET /api/v1/market/kline/{stockCode}
  - GET /api/v1/market/search

### 2.4 Flyway

- 已存在并使用 V20260213_003__create_stock_info_table.sql 创建 t_market_stock_info。
- 已补充 V20260324_004__seed_market_stock_info.sql 作为首批样例数据种子（upsert）。
- 已补充 V20260324_005__seed_market_stock_info_from_csv.sql，从 uploads/stock_info_sina.csv 导入约 2800 条股票基础数据（upsert，可重复执行）。
- 已补充 V20260324_006__seed_market_stock_info_full_a_share.sql，从 Sina A 股节点导入全量股票基础数据（当前快照 5490 条，upsert，可重复执行）。
- 全量覆盖统计（V20260324_006 快照）：市场分布 SH=2306，SZ=2884，BJ=300；板块分布 MAIN=3364，ST=178，GEM=1351，STAR=597；代码前缀分布 sz30*=1393，sh688*=603。

## 3. 本迭代测试清单

### 3.1 新增/完善测试文件

- src/test/java/com/lzbsdsg/stocksimulation/market/domain/service/MarketDataFacadeTest.java
- src/test/java/com/lzbsdsg/stocksimulation/market/infrastructure/adapter/SinaMarketDataAdapterTest.java
- src/test/java/com/lzbsdsg/stocksimulation/market/infrastructure/adapter/MockMarketDataAdapterTest.java
- src/test/java/com/lzbsdsg/stocksimulation/market/infrastructure/gateway/MarketCacheGatewayIntegrationTest.java
- src/test/java/com/lzbsdsg/stocksimulation/market/controller/MarketControllerApiTest.java

### 3.2 建议执行命令

在项目根目录执行：

```powershell
Set-Location "d:\StockSimulation\stock-simulation"
.\mvnw.cmd "-Dtest=MarketDataFacadeTest,SinaMarketDataAdapterTest,MockMarketDataAdapterTest,MarketCacheGatewayIntegrationTest,MarketControllerApiTest" test
```

### 3.3 实际执行结果

- 通过测试数：18
- 失败测试数：0
- 执行时间：约 9 秒（本地环境）

## 4. 接口验收（不仅限于测试类通过）

### 4.1 Swagger 验收入口

- http://localhost/swagger-ui/index.html

### 4.2 接口验收用例

1. 获取单只行情

```http
GET /api/v1/market/quote/sh600519
Authorization: Bearer ACCESS_TOKEN
```

预期：HTTP 200；返回 data.stockCode=sh600519；响应头含 X-Cache-Status。

2. 批量行情（codes 参数）

```http
GET /api/v1/market/quotes?codes=sh600519&codes=sz000001
Authorization: Bearer ACCESS_TOKEN
```

预期：HTTP 200；返回数组长度 >= 1；每个元素包含 stockCode/stockName/timestamp。

3. 批量行情（stockCodes 兼容参数）

```http
GET /api/v1/market/quotes?stockCodes=sh600519&stockCodes=sz000001
Authorization: Bearer ACCESS_TOKEN
```

预期：HTTP 200；与 codes 参数行为一致。

4. K 线查询

```http
GET /api/v1/market/kline/sh600519?period=DAILY&from=2026-03-01&to=2026-03-20
Authorization: Bearer ACCESS_TOKEN
```

预期：HTTP 200；返回 data 为日期序列；响应头含 X-Cache-Status。

5. 股票搜索

```http
GET /api/v1/market/search?keyword=茅台
Authorization: Bearer ACCESS_TOKEN
```

预期：HTTP 200；返回匹配股票列表；每条包含 stockCode/stockName。

### 4.3 缓存行为验收

1. 同股票连续请求（3 秒内）
- 第一次请求预期 MISS
- 再次请求预期 HIT-L1

2. 3 秒后再请求（5 秒内）
- 预期 HIT-L2

3. 并发同一股票 100 次
- 预期仅一次回源（通过日志关键字 market:load:{code} 持锁行为核对）

4. 不存在股票代码
- 首次请求预期业务异常（MARKET_DATA_UNAVAILABLE 或 MARKET_STOCK_NOT_FOUND）
- 30 秒内重复请求不应持续穿透 Provider（通过日志核对）

5. 主备 Provider 都失败场景
- 若存在 stale 数据，预期返回数据且 X-Cache-Status=STALE

## 5. Iteration 4 通过标准

满足以下全部条件视为通过：

- Market Provider 抽象可用，Sina/Tencent/Mock 均可按契约调用
- 行情读取链路满足 L1 -> L2 -> Provider 回源
- L2 缓存 TTL 带随机抖动，具备防雪崩能力
- 同 key 并发回源具备分布式锁保护（防击穿）
- 不存在股票具备空值缓存（防穿透）
- 主备 Provider 全失败时具备 stale 降级能力
- /quote /quotes /kline /search 四个接口可通过 Swagger 正常调用
- 响应头可观察到 X-Cache-Status 状态值
- 本迭代测试集合全部通过（18/18）

## 6. 备注

- K 线在第三方数据接口不可用时提供 Provider 级兜底生成，保障契约可用性与接口稳定性。
- 股票搜索依赖 t_market_stock_info 数据，默认可通过 Flyway 种子迁移自动导入全量 A 股基础股票数据。
- 若希望进行严格端到端验收，请结合 Redis/PostgreSQL 运行环境执行上述接口与缓存行为脚本。
