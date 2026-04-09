# Iteration 10 交付说明（前端 MVP：认证 + 行情）

## 1. 迭代目标

基于 `docs/doc-D-dev-roadmap.md` 的 Iteration 10 要求，完成并交付：

- 认证页面闭环（登录/注册/忘记密码）
- 前端鉴权链路（路由守卫 + 401 自动刷新重试）
- 行情页面与详情页（搜索、列表、K线）
- STOMP over SockJS 实时推送（JWT 鉴权 + 指数退避重连）
- 缓存/限流头可视化（`X-Cache-Status`、`X-RateLimit-*`）
- 迭代10可执行验收文档（功能、接口、性能）

## 2. 本次完成项

### 2.1 认证与路由

- 新增 `AuthLayout` 与认证页面：
  - `LoginPage`
  - `RegisterPage`
  - `ForgotPasswordPage`
- 新增 `useAuthStore`：
  - `login / register / forgotPassword / resetPassword / refreshAccessToken / logout`
  - 登录态持久化（localStorage）与跨模块同步
- 新增路由守卫：
  - 未登录访问业务页自动跳转 `/login`
  - 已登录访问认证页自动跳转 `/dashboard`

### 2.2 HTTP 请求链路

- 重构 `src/api/request.ts`：
  - 统一解析后端 `Result<T>` 响应
  - 自动附带 `Authorization: Bearer <accessToken>`
  - 401 自动触发 `/api/v1/auth/refresh`，成功后重放原请求
  - 解析并上报 `X-RateLimit-*`、`X-Cache-Status`
  - 429 友好提示（Element Plus Message）

### 2.3 行情页面与组件

- 新增页面：
  - `MarketPage`（行情列表 + 头部元信息 + 搜索）
  - `StockDetailPage`（单票详情 + K线 + 迭代提示）
  - `DashboardPage`（登录后默认页）
- 新增组件：
  - `StockSearch`
  - `QuoteCard`
  - `MarketOverview`
  - `KLineChart`
- K线图能力：
  - ECharts K线
  - MA5 / MA10 均线
  - 成交量子图
  - `dataZoom` 缩放

### 2.4 实时推送

- 新增 `useWebSocket`：
  - STOMP over SockJS（`/ws/market`）
  - JWT 鉴权（query token + STOMP header）
  - 指数退避重连：`1s -> 2s -> 4s -> 8s -> 16s -> 30s`
  - 推送延迟检测：`wsPushTsMillis` 超过 5s 标记降级并降低前端渲染频率
- 新增 `useMarketStore`：
  - `quotes` 状态管理
  - 订阅管理（按股票代码）
  - K线加载与详情联动

### 2.5 验收配套补齐

- `k6/market-load-test.js` 从 TODO 改为可执行脚本：
  - 压测 `/quote /quotes /kline /search`
  - 校验响应码与 `X-Cache-Status`
  - 支持 `TOKEN/VUS/DURATION` 环境变量
- 路线图迭代10任务已回填为完成状态（`[x]`）

### 2.6 历史K线真实数据化（补充）

- 后端新增历史日K持久化与同步机制：
  - `t_market_kline_daily`：真实日K落库
  - `t_market_kline_sync_state`：按股票记录“当日是否已同步”
- 新增东方财富历史K线网关，日K数据来源改为真实数据
- 复权口径可配置：`market.kline.fqt`（默认 `1` 前复权，可切 `0` 不复权）
- `MarketDataFacade#getKLine` 改为走历史K线服务，不再读写 Redis K线缓存
- 同一股票在同一自然日仅触发一次增量回源（其余请求走数据库）
- 周K/月K改为由已落库日K聚合生成（符合“日K为最小单位”）
- 历史K线仅保留最近3年数据（滚动窗口）
- 仅在进入股票详情并请求K线时触发该股票增量同步（非全量日更）

## 3. 关键变更文件

- `stock-simulation-web/src/api/request.ts`
- `stock-simulation-web/src/api/auth.ts`
- `stock-simulation-web/src/api/market.ts`
- `stock-simulation-web/src/stores/auth.ts`
- `stock-simulation-web/src/stores/market.ts`
- `stock-simulation-web/src/composables/useWebSocket.ts`
- `stock-simulation-web/src/router/index.ts`
- `stock-simulation-web/src/layouts/AuthLayout.vue`
- `stock-simulation-web/src/layouts/DefaultLayout.vue`
- `stock-simulation-web/src/pages/auth/LoginPage.vue`
- `stock-simulation-web/src/pages/auth/RegisterPage.vue`
- `stock-simulation-web/src/pages/auth/ForgotPasswordPage.vue`
- `stock-simulation-web/src/pages/market/MarketPage.vue`
- `stock-simulation-web/src/pages/market/StockDetailPage.vue`
- `stock-simulation-web/src/components/market/KLineChart.vue`
- `stock-simulation-web/src/components/market/StockSearch.vue`
- `stock-simulation-web/src/components/market/QuoteCard.vue`
- `stock-simulation-web/src/components/market/MarketOverview.vue`
- `stock-simulation-web/src/style.css`
- `k6/market-load-test.js`
- `src/main/java/com/lzbsdsg/stocksimulation/market/domain/service/HistoricalKLineService.java`
- `src/main/java/com/lzbsdsg/stocksimulation/market/infrastructure/gateway/EastMoneyKLineGateway.java`
- `src/main/java/com/lzbsdsg/stocksimulation/market/domain/repository/MarketKLineDailyRepository.java`
- `src/main/java/com/lzbsdsg/stocksimulation/market/domain/repository/MarketKLineSyncStateRepository.java`
- `src/main/java/com/lzbsdsg/stocksimulation/market/infrastructure/persistence/MarketKLineDailyMapper.java`
- `src/main/resources/db/migration/V20260325_003__create_market_kline_tables.sql`
- `docs/doc-D-dev-roadmap.md`

## 4. 测试命令与结果

### 4.1 前端静态检查 + 构建 + 单测（已执行）

```cmd
cd /d d:\StockSimulation\stock-simulation\stock-simulation-web
pnpm lint
pnpm build
pnpm test
```

结果：

- `lint` 通过
- `build` 通过
- `test` 通过（`Test Files: 2 passed`, `Tests: 4 passed`）

### 4.2 后端联调基线测试（已执行）

```cmd
cd /d d:\StockSimulation\stock-simulation
mvnw.cmd "-Dtest=AuthControllerApiTest,MarketControllerApiTest,MarketWebSocketHandlerTest" test
```

结果：`Tests run: 16, Failures: 0, Errors: 0, Skipped: 0`

### 4.3 历史K线后端回归测试（已执行）

```cmd
cd /d d:\StockSimulation\stock-simulation
mvnw.cmd -Dtest='MarketDataFacadeTest,HistoricalKLineServiceTest,EastMoneyKLineGatewayTest,MarketControllerApiTest' test
```

结果：`Tests run: 16, Failures: 0, Errors: 0, Skipped: 0`

## 5. 验收标准（不只测试通过）

### 5.1 路线图功能标准（Iteration 10）

1. 认证页面完整可用：登录/注册/忘记密码
2. 登录态守卫生效：未登录无法进入 `/dashboard` 与 `/market`
3. 401 自动刷新并重试原请求，不要求手动重新登录
4. 行情列表、搜索、详情、K线可用
5. WS 断线自动重连且采用指数退避
6. K线支持日K/周K/月K + MA + 成交量 + dataZoom
7. 429 场景有友好提示

### 5.2 接口与联调标准

1. `/api/v1/auth/*` 与前端表单映射一致
2. `/api/v1/market/*` 在 UI 中可拉取并展示
3. 前端可读取 `X-RateLimit-*` 与 `X-Cache-Status`
4. WS 订阅 `/topic/market/quote/{stockCode}` 可持续接收推送
5. 历史K线接口使用真实日K数据源，日内重复请求不重复回源

### 5.3 稳定性与可运维标准

1. 刷新令牌并发仅执行一次（队列化）
2. WS 推送延迟超过 5s 时前端出现降级标记
3. 构建产物可成功打包，浏览器页面无白屏
4. 同一股票同一天仅一次历史K线增量同步（以自动化测试和同步状态表验证）

### 5.4 性能标准（验收口径）

1. `market quote` 接口 P99 < 200ms
2. `market quotes/search` 接口 P99 < 220ms
3. `market kline` 接口 P99 < 300ms
4. `http_req_failed` < 1%

## 6. 具体验收方法（可执行）

### 6.1 启动服务

1. 后端
```cmd
cd /d d:\StockSimulation\stock-simulation
mvnw.cmd spring-boot:run
```

1. 前端
```cmd
cd /d d:\StockSimulation\stock-simulation\stock-simulation-web
pnpm dev
```

默认访问：`http://localhost:5173`

### 6.2 认证与守卫验收

1. 打开 `/login`，输入账号密码登录，预期跳转 `/dashboard`
2. 手动访问 `/market`，预期可进入（已登录）
3. 清理 `localStorage` 后直接访问 `/market`，预期跳转 `/login`

### 6.3 401 自动刷新验收

1. 先正常登录，确保本地存在 `ss_refresh_token`
2. 浏览器控制台执行：
```js
localStorage.setItem('ss_access_token', 'invalid-token')
```
1. 在行情页点击“刷新行情”
2. 预期：请求不报 401 页面错误，自动 refresh 后数据正常返回

### 6.4 行情页面与 K 线验收

1. 在 `/market` 使用搜索框输入 `sh600519` 或 `茅台`
2. 点击卡片进入 `/market/sh600519`
3. 切换 日K/周K/月K
4. 预期：K线正常渲染，MA 与成交量同步变化，支持 dataZoom 拖拽

### 6.4.1 历史日K真实性与“每日一次更新”验收

1. 执行同一请求两次（示例）：
```cmd
curl "http://localhost:8080/api/v1/market/kline/sh600519?period=DAILY&from=2025-09-26&to=2026-03-25"
curl "http://localhost:8080/api/v1/market/kline/sh600519?period=DAILY&from=2025-09-26&to=2026-03-25"
```
1. 检查数据库：
```sql
SELECT stock_code, last_sync_date, last_bar_date
FROM t_market_kline_sync_state
WHERE stock_code = 'sh600519';
```
1. 通过标准：
   - 第一次请求后存在同步状态记录
   - 同一天再次请求不重复触发增量同步（`last_sync_date` 不变化）
   - `t_market_kline_daily` 有对应日期范围的真实日K记录

### 6.5 WebSocket 与降级验收

1. 登录后进入行情页面，观察 `WS 连接状态` 显示 `CONNECTED`
2. 暂停网络后恢复，预期状态经历 `RECONNECTING -> CONNECTED`
3. 当推送延迟增大时（可通过压测制造拥塞），`推送延迟` 增长且页面出现降级提示

### 6.6 限流友好提示验收

1. 对 OTP 发送接口进行高频请求（可在注册/忘记密码页重复发送）
2. 命中 429 后，预期前端弹出“请求过于频繁，请稍后重试”提示

### 6.7 性能验收（k6）

1. 获取有效 `accessToken`（登录后从浏览器 localStorage 获取 `ss_access_token`）
2. 执行：
```cmd
cd /d d:\StockSimulation\stock-simulation
k6 run -e BASE_URL=http://localhost:8080 -e TOKEN=<accessToken> -e VUS=100 -e DURATION=5m k6/market-load-test.js
```
1. 通过标准：
   - `http_req_failed rate < 0.01`
   - `http_req_duration{endpoint:quote} p(99) < 200`
   - `http_req_duration{endpoint:quotes} p(99) < 220`
   - `http_req_duration{endpoint:kline} p(99) < 300`
   - `http_req_duration{endpoint:search} p(99) < 220`

### 6.8 WS 连接压测验收（可选）

```cmd
cd /d d:\StockSimulation\stock-simulation
k6 run -e WS_URL=ws://localhost:8080/ws/market/websocket -e WS_TOKEN=<accessToken> -e TARGET_CODE=sh600519 -e WS_SESSION_MS=5000 k6/websocket-load-test.js
```

通过标准：

- `ws_push_latency_ms p(99) < 500`
- `ws_latency_samples_total count > 0`
- 连接稳定，无持续异常断开

## 7. 交付结论

Iteration 10 已完成并具备交付条件：

- 路线图定义的前端能力全部落地
- 前端构建/测试通过，后端联调基线测试通过
- 提供了接口验收、功能验收、性能验收的完整可执行方法

该模块可用于下一迭代（交易 + 持仓前端）继续扩展。
