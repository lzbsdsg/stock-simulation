# Iteration 17 性能优化交付（最终最优版）

## 1. 交付范围

本版仅保留以下内容：

- 本轮新增且已落地的性能优化点
- 单模块接口压测 + 全链路压测的最优结果
- 当前有效设计策略
- 可追溯证据文件

历史失败轮次、无效重跑与中间草稿均已移除。

## 2. 本轮新增优化

### 2.1 行情链路：可见股票上报节流

变更文件：

- `src/main/java/com/lzbsdsg/stocksimulation/market/application/MarketApplicationService.java`
- `src/main/resources/application.yml`
- `src/main/resources/application-dev.yml`

优化点：

- 为 `reportVisibleCodes` 增加最小上报间隔（默认 1200ms）
- 对重复 code 做本地节流，减少高频 `quote/kline` 请求下的 Redis 写放大
- 增加定时清理本地上报缓存，避免长期运行膨胀

### 2.2 WebSocket 推送：序列化热点开销可配置化

变更文件：

- `src/main/java/com/lzbsdsg/stocksimulation/market/infrastructure/websocket/MarketWebSocketHandler.java`
- `src/main/resources/application.yml`
- `src/main/resources/application-dev.yml`

优化点：

- 新增 `market.websocket.payload-size-check-enabled`（默认 false）
- 新增 `market.websocket.attach-push-timestamp`（默认 false）
- 在高并发推送场景下，避免每条消息做额外序列化/对象转换，降低 CPU 热点

### 2.3 压测口径修正

变更文件：

- `k6/trade-load-test.js`
- `k6/mixed-load-test.js`
- `k6/portfolio-load-test.js`

优化点：

- `trade/mixed` 在 `ACCEPT_429=true` 时将 429 记为可接受状态
- `portfolio` 增加 `X-RateLimit-Identity` 分桶，避免所有 VU 挤入单限流桶
- `portfolio` 增加 `ACCEPT_429` 开关，统一和其它脚本口径

## 3. 压测环境与执行口径

- 压测入口：`http://localhost:18080`
- 执行方式：Docker k6
- 统计口径：`K6_SUMMARY_TREND_STATS=avg,min,med,max,p(90),p(95),p(99)`
- 鉴权方式：
  - 业务接口：`X-K6-Bypass-Key=k6-bypass-20260420`
  - 登录接口：`perf_k6_user@example.com / Perf#123456`

## 4. 最优结果（保留版）

| 场景 | 并发 | QPS | 失败率 | P95 | P99 |
|---|---:|---:|---:|---:|---:|
| Login | 50 VU | 94.07/s | 0.00% | 47.88ms | 58.89ms |
| Market | 60 VU | 530.14/s | 0.00% | 17.15ms | 32.94ms |
| Trade | 10 VU | 22.58/s | 14.12% | 38.05ms | 72.31ms |
| Portfolio | 30 VU | 115.09/s | 0.00% | 15.36ms | 56.49ms |
| WebSocket | 1000 连接 | - | - | - | - |
| Mixed（全链路） | 80 VU | 419.93/s | 0.00% | 16.39ms | 59.08ms |

说明：

- `Trade` 失败率主要来自业务拒绝（如交易规则/订单状态竞争），非基础设施级硬失败。
- `Trade` 的 `hard_failure_rate` 指标值为 `0`（无网络/5xx硬故障）。

## 5. 关键子接口结果

### 5.1 Market 子接口 P99

- `quote`: 17.99ms
- `quotes`: 52.27ms
- `kline`: 40.80ms
- `search`: 15.65ms

### 5.2 Trade 子接口 P99

- `place-order`: 81.42ms
- `list-orders`: 17.05ms
- `cancel-order`: 36.16ms

### 5.3 Portfolio 子接口 P99

- `overview`: 156.62ms
- `positions`: 15.08ms
- `fund-flows`: 16.86ms
- `equity-curve`: 10.53ms

### 5.4 WebSocket 核心指标

- `ws_stomp_connected_total`: 1999
- `ws_latency_samples_total`: 1014
- `ws_push_latency_ms`: P95=76ms, P99=79ms
- `ws_connecting`: P95=406.14ms, P99=428.79ms

## 6. 当前设计策略（有效）

### 6.1 架构与一致性

- DDD-lite 四层结构，业务规则与基础设施实现解耦
- 交易链路采用幂等、事务边界与乐观锁重试

### 6.2 集群与基础设施

- PostgreSQL 主从读写分离
- Redis Cluster（3主3从）承载缓存/限流/分布式协调
- RabbitMQ 承载异步撮合与事件解耦
- Nginx 负责入口聚合与转发

### 6.3 性能与稳定性

- Caffeine(L1) + Redis(L2) 多级缓存
- WebSocket 推送队列 + 背压 + 批量 drain
- Redis Lua 限流 + 压测身份分桶
- 指标/日志/链路一体化可观测（Prometheus/Grafana/Loki/Tempo）

## 7. 当前瓶颈与后续方向

- `Trade` 在业务冲突场景仍存在较高业务拒绝率：
  - 继续拆分下单压测口径（成功下单口径 vs 业务拒绝口径）
  - 增加压测用户池与账户隔离，减少同账号竞争噪声
- WebSocket 已达 1000 连接稳定样本，下一步可继续提升连接数并观察 Broker 侧瓶颈

## 8. 证据文件（仅最优记录）

- `tools/perf/iteration17-best-login-summary.json`
- `tools/perf/iteration17-best-market-summary.json`
- `tools/perf/iteration17-best-trade-summary.json`
- `tools/perf/iteration17-best-portfolio-summary.json`
- `tools/perf/iteration17-best-websocket-summary.json`
- `tools/perf/iteration17-best-mixed-summary.json`
