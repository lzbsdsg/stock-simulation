# 2026-04-26 性能压测报告

## 1. 结论摘要

- 当前项目在 **HTTP 推荐稳态档** 下，已经验证可稳定承载：
  - `FULL_CHAIN_RPS=650`
  - `QUOTE_RPS=260`
  - `PORTFOLIO_RPS=130`
  - `TRADE_LIST_RPS=130`
- 在该档位下，`60s` 稳态压测结果为：
  - 总吞吐：`1755.48 req/s`
  - 总延迟：`p95=11.25ms`，`p99=30.70ms`
  - 全链路业务成功：`39001`
  - 全链路业务失败：`0`
  - 业务成功总数：`109205`
  - 业务失败总数：`0`
- 继续上探到 `800/320/160/160` 后，虽然 `http_reqs.rate` 继续升高到 `2185.69 req/s`，但 `business_failure_total=8642`、`full_chain_failure_total=4885`，已经进入 **不稳定区**，因此不作为推荐容量。
- 查询接口族在单独顺序压测时，总吞吐达到 `1301.61 req/s`，优化后的 `portfolio_overview` / `portfolio_positions` P95 已从约 `500ms` 降到约 `21ms`。
- WebSocket 在顺序压测下已验证 **500 并发连接、500 次 STOMP 连接成功、3500 条推送样本**，`ws_connect_p95=291.73ms`、`ws_push_latency_p95=41ms`。

## 2. 测试环境

- 时间：`2026-04-26`
- 压测入口：`Nginx -> app-1/app-2`
- 运行拓扑：
  - `stock-nginx`
  - `stock-app-1`
  - `stock-app-2`
  - PostgreSQL 主从
  - Redis Cluster
  - RabbitMQ
- 压测工具：
  - `grafana/k6:1.7.1`
  - `tools/perf/collect-actuator-samples.ps1`
- 鉴权方式：
  - `X-K6-Bypass-Key=k6-bypass-20260420`
- 观测口径：
  - HTTP 业务是否成功，优先看 `business_success_total/business_failure_total`
  - 全链路是否成功，优先看 `full_chain_success_total/full_chain_failure_total`
  - JVM/GC/线程/连接池峰值，来自两个应用实例的 `/actuator/prometheus`

说明：

- 由于脚本启用了 `http.expectedStatuses(200, 429)`，`http_req_failed` 在导出 JSON 中并不总能准确表达业务失败，因此本报告把 **业务计数器** 作为验收口径。
- 2026-04-26 后续修订中，HTTP 脚本已取消在 `http_req_failed` 这个反向指标上配置 threshold，改为使用正向指标 `http_expected_status_rate`。后续 JSON 中应优先查看 `http_expected_status_rate.thresholds`、`business_success_rate.thresholds` 和业务失败计数器，避免把 `http_req_failed` 下的 `passes/fails` 反向理解。

## 3. 方法与验收口径

### 3.1 方法

1. 清理旧压测产物，只保留本轮可复现结果。
2. 先用低档位预热，拉起缓存、连接池和线程池到稳态。
3. 对 HTTP 混合链路做逐档上探：`A -> D -> E -> F -> upper-bound`
4. 对查询接口族做顺序压测，不与 WebSocket 并发。
5. 对 WebSocket 做顺序压测，不与查询场景并发。
6. 每个正式场景同时采集两个应用实例的 Actuator 指标。

### 3.2 本轮采用的验收口径

- HTTP 推荐稳态档：
  - `business_failure_total = 0`
  - `full_chain_failure_total = 0`
  - 总体 `p95 < 20ms`
  - 全链路 `p99 < 60ms`
- HTTP 上限探测：
  - 一旦 `business_failure_total > 0` 或 `full_chain_failure_total > 0`，直接判为不稳定
- 查询接口族：
  - `endpoint_failure_total <= 1`
  - 重点关注资产类接口尾延迟，而不以最慢接口强行卡死全部结论
- WebSocket：
  - `ws_connect_success_rate >= 99%`
  - `ws_stomp_connected_total = ws_sessions`
  - `ws_push_latency_p95 < 50ms`

## 4. HTTP 混合链路结果

### 4.1 候选档位对比

| 档位 | 参数 | 总吞吐(req/s) | 总 p95(ms) | 总 p99(ms) | 全链路 p95(ms) | 全链路 p99(ms) | 业务失败 | 全链路失败 | 结论 |
|---|---|---:|---:|---:|---:|---:|---:|---:|---|
| A | `450/180/90/90` | 1219.37 | 11.73 | 29.09 | 29 | 46 | 0 | 0 | 稳定 |
| D | `550/220/110/110` | 1494.48 | 20.33 | 66.35 | 42 | 168 | 0 | 0 | 稳定，但尾延迟开始上升 |
| E | `600/240/120/120` | 1628.29 | 15.14 | 37.92 | 37 | 67 | 0 | 0 | 稳定 |
| F | `650/260/130/130` | 1755.48 | 11.25 | 30.70 | 31 | 47 | 0 | 0 | **推荐稳态档，优化后复测** |
| Upper | `800/320/160/160` | 2185.69 | 13.05 | 34.95 | 33 | 57 | 8642 | 4885 | 不稳定 |

参数说明顺序：

- `FULL_CHAIN_RPS / QUOTE_RPS / PORTFOLIO_RPS / TRADE_LIST_RPS`

### 4.2 推荐稳态档详细结果

推荐稳态档采用：

- `FULL_CHAIN_RPS=650`
- `QUOTE_RPS=260`
- `PORTFOLIO_RPS=130`
- `TRADE_LIST_RPS=130`

结果：

- 总吞吐：`1755.48 req/s`
- 总请求数：`109205`
- 总延迟：`p95=11.25ms`，`p99=30.70ms`
- 全链路业务成功：`39001`
- 全链路业务失败：`0`
- 全链路耗时：`p95=31ms`，`p99=47ms`
- 行情独立场景：`250.79 qps`，`p95=2.61ms`
- 资产总览独立场景：`125.40 qps`，`p95=26.42ms`
- 订单列表独立场景：`125.40 qps`，`p95=5.89ms`

### 4.3 推荐稳态档的 JVM / GC / 资源观测

`recommended-rerun-actuator.csv` 汇总：

| 实例 | 峰值进程 CPU | 峰值系统 CPU | 峰值堆内存 | 峰值线程数 | 峰值主库连接 | 峰值从库连接 | GC 次数增量 | GC 暂停增量 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| `app-1` | `12.08%` | `47.40%` | `1087.08 MB` | `259` | `0` | `2` | `12` | `108 ms` |
| `app-2` | `12.11%` | `47.62%` | `1553.16 MB` | `247` | `0` | `1` | `7` | `69 ms` |

补充观察：

- 两个实例都没有把主库连接池打满，说明当前推荐档仍主要受查询与 Web 层处理能力影响，而不是数据库主写路径卡死。
- 两个实例的堆使用与线程数比上一轮推荐档更均衡，资产查询缓存没有带来明显的资源异常膨胀。
- GC 暂停总量很低，说明在推荐档下 **吞吐提升不是靠频繁 GC 换出来的**。

## 5. 查询接口族结果

场景脚本：

- `k6/perf-endpoints-common-qps.js`

顺序压测结果：

- 总吞吐：`1301.61 req/s`
- 总请求数：`81014`
- `endpoint_failure_total=0`
- 总体延迟：`p95=5.57ms`，`p99=19.66ms`

重点接口：

| 接口 | QPS | p95(ms) | p99(ms) | 说明 |
|---|---:|---:|---:|---|
| `market_quote` | `212.09` | `2.11` | `3.75` | 行情主读链路表现稳定 |
| `portfolio_overview` | `115.69` | `21.56` | `33.53` | 短 TTL 本地缓存后尾延迟显著下降 |
| `portfolio_positions` | `86.77` | `21.41` | `33.83` | 分页 count 复用与排序索引生效 |
| `trade_orders` | `96.40` | `4.92` | `7.02` | 订单查询健康 |

结论：

- 行情、订单、自选股、通知等读接口整体健康。
- 资产总览与持仓分页的 P95/P99 已从秒级长尾回落到几十毫秒级，10.1 的短 TTL 缓存、分页 count 复用和索引优化已通过复测。
- 资产类接口仍比纯缓存行情接口更重，后续若要继续压低 P99，可以优先评估账户/持仓快照与结果聚合字段缓存。

## 6. WebSocket 结果

场景脚本：

- `k6/websocket-load-test.js`

顺序压测参数：

- `VUS=500`
- `DURATION=60s`
- `WS_SESSION_MS=60000`
- `WS_HEARTBEAT_MS=10000`

结果：

- `ws_sessions=500`
- `ws_connect_success_rate=100%`
- `ws_stomp_connected_total=500`
- `ws_latency_samples_total=3500`
- `ws_connect_p95=291.73ms`
- `ws_push_latency_p95=41ms`

Actuator 观测：

| 实例 | 峰值进程 CPU | 峰值堆内存 | 峰值线程数 | 峰值 WS 连接 | 峰值队列等待 | 峰值推送耗时 | GC 次数增量 | GC 暂停增量 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| `app-1` | `19.23%` | `303.69 MB` | `628` | `500` | `293 ms` | `123.81 ms` | `2` | `25 ms` |
| `app-2` | `7.47%` | `351.44 MB` | `441` | `0` | `586 ms` | `1.21 ms` | `0` | `0 ms` |

说明：

- 本轮 WebSocket 压测全部从单一压测源发起，Nginx `ip_hash` 会把同源连接基本固定到同一实例，因此 `500` 条连接几乎都落在 `app-1`。
- 这意味着当前结果更接近 **“单实例可承载 500 同源连接”**，而不是双实例平均分摊后的总容量。
- 若要验证真正的集群总 WS 容量，需要多源压测器或改变粘性策略。

## 7. 为什么当前设计能达到这个性能

### 7.1 HTTP 侧

- 行情接口使用 `Caffeine(L1) + Redis(L2)`，让高频 quote 查询多数在缓存层结束。
- 查询流量走主从分离，从库承担大部分列表和资产读请求。
- 撮合与通知异步化，HTTP 下单链路只做短事务和必要冻结，不在事务里等待 MQ / 外部调用。
- Nginx 统一入口让双实例可以直接横向接流量，压测口径更接近真实部署。

### 7.2 WebSocket 侧

- Redis Pub/Sub 负责实例间扇出，避免只有单实例推送。
- WebSocket 推送有队列、批量 drain 与退化发送周期，可以在连接增长时保住系统稳定。
- `MARKET_WS_ATTACH_PUSH_TIMESTAMP=true` 让推送链路具备直接测时延的能力，而不是只看握手成功。

### 7.3 JVM / 资源侧

- 推荐稳态档下 GC 次数与暂停时间都较低，说明吞吐主要来自缓存命中、读写拆分和异步解耦，而不是 JVM 频繁抖动。
- 连接池峰值很低，说明瓶颈当前不在数据库连接数，而更偏向应用层聚合查询与单次结果构造。

## 8. 复现命令

### 8.1 一键复现

在仓库根目录执行：

```cmd
powershell -ExecutionPolicy Bypass -File .\tools\perf\run-performance-suite.ps1 -Tag 20260426-monitor-balance
```

说明：

- 一键脚本会在 `tools/perf/runs/{Tag}/` 下输出 `warmup`、`recommended`、`upper-bound`、`common-qps`、`websocket` 五组原始结果。
- 本报告引用的推荐稳态档与查询族结果来自优化后择优保留的 `k6/_recommended-rerun-20260426-monitor-balance.json`、`k6/_common-qps-rerun-20260426-monitor-balance.json`。
- 上限探测与 WebSocket 复跑未产生更优且完整的结果，仍保留原更优证据 `k6/_upper-bound-20260426-opt.json`、`k6/_websocket-seq-20260426-opt.json`。

### 8.2 推荐稳态档

```cmd
set K6_WORKDIR=%cd%\k6 && docker run --rm --network stock-simulation_stock-network -v "%K6_WORKDIR%:/work" -w /work grafana/k6:1.7.1 run /work/perf-fullchain-and-endpoints.js -e BASE_URL=http://nginx -e K6_BYPASS_KEY=k6-bypass-20260420 -e ACCEPT_429=true -e DURATION=60s -e FULL_CHAIN_RPS=650 -e QUOTE_RPS=260 -e PORTFOLIO_RPS=130 -e TRADE_LIST_RPS=130 --summary-export /work/_recommended-rerun-20260426-monitor-balance.json
```

### 8.3 上限探测

```cmd
set K6_WORKDIR=%cd%\k6 && docker run --rm --network stock-simulation_stock-network -v "%K6_WORKDIR%:/work" -w /work grafana/k6:1.7.1 run /work/perf-fullchain-and-endpoints.js -e BASE_URL=http://nginx -e K6_BYPASS_KEY=k6-bypass-20260420 -e ACCEPT_429=true -e DURATION=40s -e FULL_CHAIN_RPS=800 -e QUOTE_RPS=320 -e PORTFOLIO_RPS=160 -e TRADE_LIST_RPS=160 --summary-export /work/_upper-bound-20260426-opt.json
```

### 8.4 查询接口族

```cmd
set K6_WORKDIR=%cd%\k6 && docker run --rm --network stock-simulation_stock-network -v "%K6_WORKDIR%:/work" -w /work grafana/k6:1.7.1 run /work/perf-endpoints-common-qps.js -e BASE_URL=http://nginx -e K6_BYPASS_KEY=k6-bypass-20260420 -e ACCEPT_429=true -e DURATION=60s --summary-export /work/_common-qps-rerun-20260426-monitor-balance.json
```

### 8.5 WebSocket

```cmd
set K6_WORKDIR=%cd%\k6 && docker run --rm --network stock-simulation_stock-network -v "%K6_WORKDIR%:/work" -w /work grafana/k6:1.7.1 run /work/websocket-load-test.js -e BASE_URL=http://nginx -e K6_BYPASS_KEY=k6-bypass-20260420 -e DURATION=60s -e VUS=500 -e WS_URL=ws://nginx/ws/market-native -e TARGET_CODE=sh600519 -e WS_SESSION_MS=60000 -e WS_HEARTBEAT_MS=10000 --summary-export /work/_websocket-seq-20260426-opt.json
```

### 8.6 Actuator 采样

```cmd
powershell -ExecutionPolicy Bypass -File .\tools\perf\collect-actuator-samples.ps1 -OutputCsv .\tools\perf\runs\manual\actuator.csv -DurationSeconds 75 -IntervalSeconds 5
```

## 9. 证据文件

- 推荐稳态档：
  - `k6/_recommended-rerun-20260426-monitor-balance.json`
  - `tools/perf/runs/20260426-monitor-balance/recommended-rerun.txt`
  - `tools/perf/runs/20260426-monitor-balance/recommended-rerun-actuator.csv`
- 上限探测：
  - `k6/_upper-bound-20260426-opt.json`
  - `tools/perf/runs/20260426-opt/07-upper-bound.txt`
  - `tools/perf/runs/20260426-opt/07-upper-bound-actuator.csv`
- 查询接口族：
  - `k6/_common-qps-rerun-20260426-monitor-balance.json`
  - `tools/perf/runs/20260426-monitor-balance/common-qps-rerun.txt`
- WebSocket：
  - `k6/_websocket-seq-20260426-opt.json`
  - `tools/perf/runs/20260426-opt/11-websocket-seq.txt`
  - `tools/perf/runs/20260426-opt/11-websocket-seq-actuator.csv`

## 10. 后续优化与已落地改进

### 10.1 重点优化资产查询接口

问题：

- `portfolio_overview` 和 `portfolio_positions` 是当前最明显的长尾瓶颈，查询族压测中 P99 分别达到 `1831.78ms` 和 `1968.91ms`。

已落地改进：

- 新增 `portfolioQuery` Caffeine 本地缓存区，资产总览与持仓分页结果使用 `2s` 短 TTL，降低压测和真实用户重复刷新时的账户、持仓、行情聚合开销。
- 持仓分页 repository 改为返回 `records + total`，复用 MyBatis-Plus 分页查询自带的 count 结果，避免 service 层额外再查一次 `countByUserId`。
- 新增 `idx_position_user_created_id_desc` 索引，匹配 `positions?page=...` 按 `user_id + created_at DESC + id DESC` 的分页排序路径。

复测结论：

- 查询接口族整体 `p95/p99` 从 `102.67ms / 716.03ms` 下降到 `5.57ms / 19.66ms`，`endpoint_failure_total` 从 `1` 降到 `0`。
- `portfolio_overview` 从 `p95=510.70ms`、`p99=1831.78ms` 下降到 `p95=21.56ms`、`p99=33.53ms`。
- `portfolio_positions` 从 `p95=492.88ms`、`p99=1968.91ms` 下降到 `p95=21.41ms`、`p99=33.83ms`。

建议：

- 中长期可以继续引入账户/持仓快照，把高频总览查询从实时多表聚合改为读取预聚合结果，并由交易成交、撤单、日终任务触发更新。
- 对更复杂的历史收益、组合分析类查询，可以考虑物化视图或专用 OLAP 节点承载，避免与核心交易链路争抢 OLTP 资源。

### 10.2 验证 WebSocket 集群真实容量

现状：

- 当前 `500` 连接来自单一压测源，Nginx `ip_hash` 使连接集中落到 `app-1`，该结果更接近单实例同源连接能力。

建议：

- 使用多台压测机或多个来源 IP 执行 WebSocket 压测，验证连接均匀分布后的集群总容量。
- 评估压测环境下临时切换负载均衡策略的必要性，例如独立验证非粘性连接分布，再回到生产需要的粘性策略。

### 10.3 建立持续性能基准与监控

建议：

- 将推荐稳态档 F 的参数和结果作为性能基准：`FULL_CHAIN_RPS=650`、`QUOTE_RPS=260`、`PORTFOLIO_RPS=130`、`TRADE_LIST_RPS=130`。
- 每次重要代码发布前运行相同压测套件，与基准结果对比，重点关注 QPS、P95/P99、业务失败数、GC 暂停和线程数。
- 将关键 SLA 写入生产监控告警，例如全链路 `P99 < 60ms`、`business_failure_total = 0`、`full_chain_failure_total = 0`、GC 暂停异常增长。

### 10.4 探索更高负载下的资源均衡

观察：

- 优化后推荐稳态档下，`app-1` / `app-2` 峰值堆内存分别约 `1087.08MB` / `1553.16MB`，峰值线程数分别为 `259` / `247`，资源分布比上一轮更均衡。
- WebSocket 场景仍因单一压测源与 Nginx `ip_hash` 集中落到 `app-1`，连接分布不均衡的问题仍需要单独验证。

建议：

- 先确认不均衡来源：Nginx 负载均衡策略、压测源 IP 分布、WebSocket 粘性连接、热点股票缓存和用户 ID 分布。
- 如果是热点数据导致，优先优化缓存 key 分布和热点续期策略。
- 如果是负载均衡策略导致，评估是否需要在 HTTP 与 WebSocket 使用不同 upstream 策略，提升双实例资源利用率。
