# Iteration 9 交付说明（持仓与资产模块 Portfolio）

## 1. 迭代目标

基于 `docs/doc-D-dev-roadmap.md` 的 Iteration 9 要求，完成并交付：

- 持仓与资产接口：`overview / positions / fund-flows / equity-curve`
- 资产快照服务与调度：分批处理（每批 500 可配）+ 分布式锁防重
- 快照幂等：同一用户同一日期重复执行不报错
- 分区策略落地：资金流水与资产快照按月分表路由
- 配套测试与性能验收脚本

## 2. 本次完成项

### 2.1 Portfolio 应用服务闭环

- 完成 `PortfolioApplicationService`：
  - `getOverview`：聚合 `Account + Position + 行情(batchGetQuotes)`，实时计算
    - `totalAssets = available + frozen + marketValue`
    - `totalProfit / totalProfitRate`
    - `todayProfit / todayProfitRate`
  - `getPositions`：返回持仓 + 当前价 + 持仓盈亏 + 今日盈亏
  - `getFundFlows`：分页查询资金流水（`@ReadOnly`）
  - `getEquityCurve`：收益曲线 + 最大回撤（`@ReadOnly`）
- 新增 VO：
  - `EquityCurveVO`
  - `EquityCurvePointVO`

### 2.2 控制器与接口补齐

- `PortfolioController` 新增/完善：
  - `GET /api/v1/portfolio/overview`
  - `GET /api/v1/portfolio/positions`
  - `GET /api/v1/portfolio/fund-flows`
  - `GET /api/v1/portfolio/equity-curve`
- 四个接口均补充 `@RateLimit(limit=100, window=60, ...)`

### 2.3 领域服务与调度

- 完成 `AssetSnapshotService`：
  - `createDailySnapshots(snapshotDate, userIds)` 分批快照
  - 批量行情查询（`batchGetQuotes`）
  - 计算总资产、当日收益、累计收益率
  - 幂等处理（已存在快照或唯一约束冲突时跳过）
  - `unfreezeDuePositions(today)` 到期持仓解冻

- 完成 `AssetSnapshotScheduler`：
  - 每日快照任务（默认 `15:15`）
  - Redis 分布式锁：`snapshot:daily:{date}`
  - 按用户游标分批扫描（默认 500）
  - 批次日志输出（可见 batchNo/created/skipped/failed）
  - 解冻任务（默认 `09:25`）+ 锁防重

### 2.4 仓储与基础设施补齐

- `AccountRepository` 新增批量游标方法：
  - `findUserIdsAfter(lastUserId, limit)`
- `PositionRepository` 新增：
  - `unfreezeDuePositions(today)`
- `AssetSnapshotRepository` 新增：
  - `findLatestBefore(userId, snapshotDate)`

### 2.5 分区迁移（Flyway）

- 新增迁移：`V20260325_002__add_portfolio_month_partitions.sql`
  - 为 `t_portfolio_fund_flow`、`t_portfolio_asset_snapshot` 创建按月子表
  - 增加路由触发器（写入自动进入对应月份表）
  - 保持父表查询透明兼容（查询父表可读到子表数据）

### 2.6 测试与性能脚本

- 新增/完善测试：
  - `PositionDomainServiceTest`（扩展到 7 条）
  - `AssetSnapshotServiceTest`（3 条）
  - `PositionRepositoryIntegrationTest`（3 条）
  - `FundFlowPartitionTest`（2 条）
  - `PortfolioControllerApiTest`（4 条）
  - `AssetSnapshotSchedulerTest`（3 条）
- 新增压测脚本：
  - `k6/portfolio-load-test.js`

## 3. 关键变更文件

- `src/main/java/com/lzbsdsg/stocksimulation/portfolio/application/PortfolioApplicationService.java`
- `src/main/java/com/lzbsdsg/stocksimulation/portfolio/controller/PortfolioController.java`
- `src/main/java/com/lzbsdsg/stocksimulation/portfolio/domain/service/AssetSnapshotService.java`
- `src/main/java/com/lzbsdsg/stocksimulation/portfolio/infrastructure/scheduler/AssetSnapshotScheduler.java`
- `src/main/java/com/lzbsdsg/stocksimulation/portfolio/domain/repository/AssetSnapshotRepository.java`
- `src/main/java/com/lzbsdsg/stocksimulation/portfolio/domain/repository/PositionRepository.java`
- `src/main/java/com/lzbsdsg/stocksimulation/user/domain/repository/AccountRepository.java`
- `src/main/resources/db/migration/V20260325_002__add_portfolio_month_partitions.sql`
- `k6/portfolio-load-test.js`

## 4. 测试命令与结果

### 4.1 已执行测试（迭代9相关）

```cmd
cd /d d:\StockSimulation\stock-simulation
mvnw.cmd "-Dtest=PositionDomainServiceTest,AssetSnapshotServiceTest,PositionRepositoryIntegrationTest,FundFlowPartitionTest,PortfolioControllerApiTest,AssetSnapshotSchedulerTest" test
```

结果：`Tests run: 22, Failures: 0, Errors: 0, Skipped: 0`

### 4.2 Spotless 校验（本次变更文件）

```cmd
cd /d d:\StockSimulation\stock-simulation
mvnw.cmd spotless:check "-DspotlessFiles=src/main/java/com/lzbsdsg/stocksimulation/portfolio/application/PortfolioApplicationService.java,src/main/java/com/lzbsdsg/stocksimulation/portfolio/controller/PortfolioController.java,src/main/java/com/lzbsdsg/stocksimulation/portfolio/domain/repository/AssetSnapshotRepository.java,src/main/java/com/lzbsdsg/stocksimulation/portfolio/domain/repository/PositionRepository.java,src/main/java/com/lzbsdsg/stocksimulation/portfolio/domain/service/AssetSnapshotService.java,src/main/java/com/lzbsdsg/stocksimulation/portfolio/infrastructure/persistence/AssetSnapshotMapper.java,src/main/java/com/lzbsdsg/stocksimulation/portfolio/infrastructure/persistence/AssetSnapshotRepositoryImpl.java,src/main/java/com/lzbsdsg/stocksimulation/portfolio/infrastructure/persistence/PositionMapper.java,src/main/java/com/lzbsdsg/stocksimulation/portfolio/infrastructure/persistence/PositionRepositoryImpl.java,src/main/java/com/lzbsdsg/stocksimulation/portfolio/infrastructure/scheduler/AssetSnapshotScheduler.java,src/main/java/com/lzbsdsg/stocksimulation/user/domain/repository/AccountRepository.java,src/main/java/com/lzbsdsg/stocksimulation/user/infrastructure/persistence/AccountMapper.java,src/main/java/com/lzbsdsg/stocksimulation/user/infrastructure/persistence/AccountRepositoryImpl.java,src/main/java/com/lzbsdsg/stocksimulation/portfolio/application/vo/EquityCurvePointVO.java,src/main/java/com/lzbsdsg/stocksimulation/portfolio/application/vo/EquityCurveVO.java,src/test/java/com/lzbsdsg/stocksimulation/performance/TradeConcurrencyTest.java,src/test/java/com/lzbsdsg/stocksimulation/portfolio/domain/service/AssetSnapshotServiceTest.java,src/test/java/com/lzbsdsg/stocksimulation/portfolio/domain/service/PositionDomainServiceTest.java,src/test/java/com/lzbsdsg/stocksimulation/portfolio/controller/PortfolioControllerApiTest.java,src/test/java/com/lzbsdsg/stocksimulation/portfolio/infrastructure/PositionRepositoryIntegrationTest.java,src/test/java/com/lzbsdsg/stocksimulation/portfolio/infrastructure/FundFlowPartitionTest.java,src/test/java/com/lzbsdsg/stocksimulation/portfolio/infrastructure/scheduler/AssetSnapshotSchedulerTest.java"
```

结果：通过。

## 5. 验收标准（不只测试通过）

### 5.1 功能与接口标准

1. `GET /api/v1/portfolio/overview` 返回总资产、可用、冻结、市值、收益信息  
2. `GET /api/v1/portfolio/positions` 返回持仓 + 当前价 + 盈亏  
3. `GET /api/v1/portfolio/fund-flows` 分页可用，且为只读查询路径  
4. `GET /api/v1/portfolio/equity-curve` 返回曲线点位与最大回撤  

### 5.2 设计文档一致性标准

1. 资产恒等式成立：`available + frozen + marketValue = totalAssets`  
2. 成本与盈亏计算基于 `BigDecimal`（避免精度丢失）  
3. 快照任务分批执行（批次日志可观测）  
4. 快照重复执行幂等（不重复插入，不报 500）  
5. `fund-flows / equity-curve` 使用 `@ReadOnly` 从库路由  

### 5.3 数据与可靠性标准

1. Redis 分布式锁生效（同一天仅一个实例执行快照任务）  
2. 资金流水与资产快照具备按月分表路由能力  
3. 解冻任务可批量释放到期冻结持仓  

### 5.4 性能标准（迭代9）

1. `overview / positions`：P99 < 200ms  
2. `fund-flows / equity-curve`：P99 < 300ms  
3. `http_req_failed` < 1%  

## 6. 具体验收方法（可执行）

### 6.1 启动服务

```cmd
cd /d d:\StockSimulation\stock-simulation
mvnw.cmd spring-boot:run
```

### 6.2 接口验收

1. 资产总览
```http
GET /api/v1/portfolio/overview
Authorization: Bearer <accessToken>
```
预期：HTTP 200，`totalAssets = availableBalance + frozenBalance + marketValue`

2. 持仓列表
```http
GET /api/v1/portfolio/positions
Authorization: Bearer <accessToken>
```
预期：每条记录包含 `currentPrice / marketValue / profit / profitRate`

3. 资金流水
```http
GET /api/v1/portfolio/fund-flows?page=1&size=20
Authorization: Bearer <accessToken>
```
预期：HTTP 200，分页字段 `records/total/page/size` 正常

4. 收益曲线
```http
GET /api/v1/portfolio/equity-curve?days=30
Authorization: Bearer <accessToken>
```
预期：返回 `points`，并包含 `maxDrawdown`

### 6.3 快照任务验收（分批 + 幂等 + 锁）

建议临时将 cron 调整为每分钟，便于快速观察：

```cmd
cd /d d:\StockSimulation\stock-simulation
mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--portfolio.snapshot.daily-cron=0 */1 * * * * --portfolio.snapshot.batch-size=2"
```

验收点：

1. 日志包含 `portfolio.snapshot.batch_done`（可见批次）  
2. 同日重复触发时出现 `portfolio.snapshot.skip_duplicate`（锁生效）  
3. 数据库 `t_portfolio_asset_snapshot`（父表查询）可查到当日快照  
4. 重复执行不产生重复 `(user_id, snapshot_date)` 记录  

### 6.4 分区路由验收

在 PostgreSQL 执行：

```sql
SELECT tgname
FROM pg_trigger
WHERE tgrelid = 't_portfolio_fund_flow'::regclass
   OR tgrelid = 't_portfolio_asset_snapshot'::regclass;

SELECT inhrelid::regclass AS child_table
FROM pg_inherits
WHERE inhparent IN ('t_portfolio_fund_flow'::regclass, 't_portfolio_asset_snapshot'::regclass)
ORDER BY 1;
```

预期：

1. 存在 `trg_route_portfolio_fund_flow_partition`、`trg_route_portfolio_asset_snapshot_partition`  
2. 存在按月子表 `t_portfolio_fund_flow_YYYY_MM`、`t_portfolio_asset_snapshot_YYYY_MM`  

### 6.5 性能验收（k6）

```cmd
cd /d d:\StockSimulation\stock-simulation
k6 run -e BASE_URL=http://localhost:8080 -e TOKEN=<accessToken> -e VUS=100 -e DURATION=5m -e DAYS=30 k6/portfolio-load-test.js
```

通过标准：

1. `http_req_duration{endpoint:overview} p(99) < 200ms`
2. `http_req_duration{endpoint:positions} p(99) < 200ms`
3. `http_req_duration{endpoint:fund-flows} p(99) < 300ms`
4. `http_req_duration{endpoint:equity-curve} p(99) < 300ms`
5. `http_req_failed rate < 0.01`

## 7. 交付结论

Iteration 9（Portfolio）核心能力已完成：接口、快照、分批调度、幂等、分区路由、测试与性能脚本已形成闭环。  
本次交付不只覆盖“测试通过”，也包含接口可用性、可靠性、分批任务与性能验收方法，可据本文档直接执行验收。

