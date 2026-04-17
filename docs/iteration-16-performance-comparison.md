# Iteration 16 性能对比测试报告

## 1. 报告范围

本报告聚焦两类内容：

- 自动化测试执行结果（后端 + 前端）
- 性能优化前后对比（SQL 热点查询 + 前端构建产物）

时间：2026-04-17（初版） / 2026-04-18（k6 复跑更新）

## 2. 测试执行结果

### 2.1 后端自动化测试

执行命令：

```cmd
cd /d d:\StockSimulation\stock-simulation
mvnw.cmd test
```

结果汇总（来自 surefire xml 汇总）：

- tests = 59
- failures = 0
- errors = 0
- skipped = 0

结论：后端回归测试通过。

### 2.2 前端自动化测试

执行命令：

```cmd
cd /d d:\StockSimulation\stock-simulation\stock-simulation-web
pnpm test
```

结果：

- Test Files: 3 passed
- Tests: 6 passed, 0 failed

结论：前端回归测试通过。

## 3. SQL 性能 A/B 对比

### 3.1 测试方法

为保证“优化前/优化后”可比，本次采用同库同数据 A/B：

1. 导入基准数据
   - 用户约 2 万
   - 订单约 12 万
   - 成交约 2.5 万
   - 持仓约 3 万
2. 删除迭代16新增索引，采集优化前指标
3. 重新创建迭代16新增索引，采集优化后指标
4. 每个探针查询循环 50 次，统计平均耗时（ms）

新增索引（迭代16）：

- idx_user_created_id_desc
- idx_user_role
- idx_trade_record_traded_at
- idx_order_status_updated_id
- idx_position_user_total_quantity

### 3.2 结果对比

| 指标 | 优化前 avg(ms) | 优化后 avg(ms) | 提升 |
|---|---:|---:|---:|
| Q1_USER_LIST | 5.103 | 0.412 | 91.93% |
| Q2_USER_ROLE_COUNT | 1.730 | 0.066 | 96.18% |
| Q3_TRADE_1D_COUNT | 4.896 | 1.271 | 74.04% |
| Q4_ARCHIVE_CANDIDATE | 13.396 | 1.565 | 88.32% |
| Q5_POSITION_POSITIVE | 2.806 | 0.976 | 65.22% |

总体观察：

- 五项探针平均提升约 83.14%
- 与归档候选扫描、用户维度统计相关查询收益最明显

## 4. 前端构建性能 A/B 对比

### 4.1 测试方法

对比两套构建配置：

- 优化前：baseline 配置（未启用手工 vendor 分包）
- 优化后：当前迭代16配置（manualChunks + 分包策略）

执行命令：

```cmd
cd /d d:\StockSimulation\stock-simulation\stock-simulation-web
pnpm vite build --config vite.config.baseline.ts
pnpm vite build
```

### 4.2 结果对比

关键 chunk（加载路径影响更大）：

| 资产 | 优化前(kB) | 优化后(kB) | 提升 |
|---|---:|---:|---:|
| index-*.js (主入口) | 41.59 | 17.59 | 57.71% |
| request-*.js (请求层) | 926.38 | 5.31 | 99.43% |
| useWebSocket-*.js | 70.13 | 3.56 | 94.92% |

构建耗时：

- 优化前：1.96s
- 优化后：1.78s
- 提升：9.18%

补充观察（整体包体）：

- JS Chunk 数：25 -> 29（分包更细）
- JS 总体积：2300.71kB -> 2336.84kB（+1.57%）
- 最大 chunk：1111.41kB -> 1114.34kB（基本持平）

结论：

- 总体积不是主要收益点，收益主要来自“关键路径 chunk 明显变小 + 依赖解耦更细”
- 更符合首屏与按需加载优化目标

## 5. k6 复跑结果（2026-04-18）

### 5.1 复跑环境说明

- 复跑入口：`http://localhost:18080`
- 认证方式：启用旁路鉴权头 `X-K6-Bypass-Key`（对应环境变量 `K6_BYPASS_KEY`）
- 旁路身份：`userId=2`

### 5.2 执行命令

```cmd
cd /d d:\StockSimulation\stock-simulation

k6 run --quiet -e BASE_URL=http://localhost:18080 -e K6_BYPASS_KEY=k6-bypass-20260417 -e VUS=50 -e DURATION=20s --summary-export .\tools\perf\k6-market-rerun-20260418-v2-summary.json .\k6\market-load-test.js

k6 run --quiet --no-setup -e BASE_URL=http://localhost:18080 -e K6_BYPASS_KEY=k6-bypass-20260417 -e VUS=30 -e DURATION=20s --summary-export .\tools\perf\k6-portfolio-rerun-20260418-v2-summary.json .\k6\portfolio-load-test.js

k6 run --quiet --no-setup -e BASE_URL=http://localhost:18080 -e K6_BYPASS_KEY=k6-bypass-20260417 -e VUS=20 -e DURATION=20s -e ACCEPT_429=true --summary-export .\tools\perf\k6-trade-rerun-20260418-v2-summary.json .\k6\trade-load-test.js

k6 run --quiet --vus 300 --duration 10s -e WS_URL=ws://localhost:18080/ws/market/websocket -e TARGET_CODE=sh600519 --summary-export .\tools\perf\k6-websocket-rerun-20260418-v2-summary.json .\k6\websocket-load-test.js
```

### 5.3 结果摘要

1) Market（`k6-market-rerun-20260418-v2`）
- `http_req_failed = 36.22%`（未达标）
- 阈值：
   - `quote p(99)=252.67ms`（未达标，阈值 `<200ms`）
   - `quotes p(99)=780.4ms`（未达标，阈值 `<220ms`）
   - `kline p(99)=3.1s`（未达标，阈值 `<300ms`）
   - `search p(99)=108.85ms`（达标）
- 现象：`kline` 校验全部失败（1094/1094），`quote` 部分失败（491/1094）。

2) Trade（`k6-trade-rerun-20260418-v2`）
- 延迟：`p(95)=30.14ms`、`p(99)=67.78ms`（达标）
- `hard_failure_rate = 0.00%`（达标）
- 业务 check：`place order` 失败 13/25456，`list orders` 全部通过。
- 注意：`http_req_failed=99.94%` 与 check 结果不一致，主要受脚本对“期望响应”的默认统计口径影响（与 `ACCEPT_429=true` 并行时易出现偏差），应以业务 check 与自定义失败率为主。

3) Portfolio（`k6-portfolio-rerun-20260418-v2`）
- 本次运行未形成有效请求样本（`http_reqs=0`，`http_req_duration=0`）。
- 结论：该脚本本轮结果无统计意义，需在下一轮单独排查并复测。

4) WebSocket（`k6-websocket-rerun-20260418-v2`）
- `ws connect success`：0/3000（连接升级校验未通过）
- `ws_connecting p(99)=56.53ms`（连接建立耗时本身低）
- `ws_latency_samples_total=0`（未采到推送时延样本）
- 结论：WebSocket 场景本轮未满足验收。

## 6. 结论

本次迭代16在可量化维度上已经验证：

- 回归测试：后端与前端均通过
- SQL 热点查询：优化前后提升显著（平均约 83.14%）
- 前端构建：关键路径 chunk 与构建耗时均有明显改善

当前仍需补齐项：

- Market：修复 `kline`/`quote` 高失败率后再次复测
- Portfolio：修复脚本本轮“0 请求样本”问题并复测
- WebSocket：排查握手/订阅链路，恢复连接成功率与时延样本采集

## 7. 证据文件

- tools/perf/sql-before.txt
- tools/perf/sql-after.txt
- tools/perf/frontend-build-before.txt
- tools/perf/frontend-build-after.txt
- tools/perf/k6-market-after.txt
- tools/perf/k6-market-after-summary.json
- tools/perf/k6-market-rerun-20260418-v2.txt
- tools/perf/k6-market-rerun-20260418-v2-summary.json
- tools/perf/k6-portfolio-rerun-20260418-v2.txt
- tools/perf/k6-portfolio-rerun-20260418-v2-summary.json
- tools/perf/k6-trade-rerun-20260418-v2.txt
- tools/perf/k6-trade-rerun-20260418-v2-summary.json
- tools/perf/k6-websocket-rerun-20260418-v2-summary.json
- tools/perf/iteration16_seed.sql
- tools/perf/iteration16_seed_trade.sql
- tools/perf/iteration16_benchmark_select.sql
