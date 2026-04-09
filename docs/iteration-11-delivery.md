# Iteration 11 交付说明（交易 + 持仓）

## 1. 迭代目标

基于 docs/doc-D-dev-roadmap.md 的 Iteration 11（Week 12）：

- 完成前端交易页面与持仓页面
- 支持下单/撤单/委托查询/成交记录
- 展示资产总览、持仓盈亏、收益曲线、资金流水
- 支持在股票详情页直接下单

## 2. 本次完成项

### 2.1 前端交易模块

- 新增 Trade 页面与组件：
  - stock-simulation-web/src/pages/trade/TradePage.vue
  - stock-simulation-web/src/components/trade/OrderForm.vue
  - stock-simulation-web/src/components/trade/OrderList.vue
  - stock-simulation-web/src/components/trade/TradeHistory.vue
- 新增交易 Store 与 API：
  - stock-simulation-web/src/stores/trade.ts
  - stock-simulation-web/src/api/trade.ts
  - stock-simulation-web/src/types/trade.ts
- 交易表单能力：
  - 100 股整数倍校验
  - clientOrderId UUID 幂等键
  - 按钮 loading 防重复提交

### 2.2 前端持仓模块

- 新增 Portfolio 页面与组件：
  - stock-simulation-web/src/pages/portfolio/PortfolioPage.vue
  - stock-simulation-web/src/components/portfolio/AssetOverview.vue
  - stock-simulation-web/src/components/portfolio/PositionTable.vue
  - stock-simulation-web/src/components/portfolio/EquityCurve.vue
  - stock-simulation-web/src/components/portfolio/FundFlowTable.vue
- 新增持仓 Store 与 API：
  - stock-simulation-web/src/stores/portfolio.ts
  - stock-simulation-web/src/api/portfolio.ts
  - stock-simulation-web/src/types/portfolio.ts

### 2.3 现有页面升级

- StockDetailPage 接入直接下单与委托/成交面板：
  - stock-simulation-web/src/pages/market/StockDetailPage.vue
- Dashboard 增加资产总览信息：
  - stock-simulation-web/src/pages/DashboardPage.vue
- 新增路由：
  - /trade
  - /portfolio
  - stock-simulation-web/src/router/index.ts

## 3. 核心接口验收点

- POST /api/v1/trade/orders
- DELETE /api/v1/trade/orders/{orderId}
- GET /api/v1/trade/orders
- GET /api/v1/trade/trades
- GET /api/v1/portfolio/overview
- GET /api/v1/portfolio/positions
- GET /api/v1/portfolio/fund-flows
- GET /api/v1/portfolio/equity-curve

## 3.1 遗漏补齐（2026-04）

- 交易面板实时刷新补齐：
  - `stock-simulation-web/src/pages/trade/TradePage.vue`
  - `stock-simulation-web/src/pages/market/StockDetailPage.vue`
  - 补齐内容：增加 3s 轻量轮询刷新委托与成交列表，并在页面卸载时清理定时器。
- 股票详情下单联动补齐：
  - `stock-simulation-web/src/components/trade/OrderForm.vue`
  - 补齐内容：`stockCode` props 改为可响应路由切换，避免切股后下单代码未同步。

## 4. 已执行命令与结果

### 4.1 后端测试（已执行）

```cmd
call .\mvnw.cmd -Dtest=TradeApplicationServiceTest,PortfolioControllerApiTest test
```

结果：通过（含在本次核心用例集合中，0 失败）。

### 4.2 前端检查（已执行）

```cmd
cd /d d:\StockSimulation\stock-simulation\stock-simulation-web
pnpm lint
pnpm test
pnpm build
```

结果：
- lint 通过
- test 通过
- build 通过

## 5. 功能验收步骤

1. 启动后端与前端：

```cmd
cd /d d:\StockSimulation\stock-simulation
call .\mvnw.cmd spring-boot:run
```

```cmd
cd /d d:\StockSimulation\stock-simulation\stock-simulation-web
pnpm dev
```

2. 交易验收：
- 打开 /trade 或 /market/{stockCode}
- 提交下单（100股整数倍）
- 校验委托列表刷新、可撤单
- 查看成交记录展示

3. 持仓验收：
- 打开 /portfolio
- 校验资产总览、持仓盈亏、收益曲线、资金流水

## 6. 通过标准

- 可在 StockDetailPage 直接下单
- 委托列表可实时刷新并支持撤单
- 持仓浮盈浮亏展示正常
- 收益曲线可展示并切换区间
- Dashboard 资产卡片可展示
- 构建与测试通过
