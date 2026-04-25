# Iteration 17 / Iteration 18 性能整合说明

时间：2026-04-25  
环境：本地完整 Docker 拓扑  
项目目录：`D:\StockSimulation\stock-simulation`

## 1. 这份说明解决什么问题

Iteration 17 和 Iteration 18 都在做性能压测，但它们的目的不一样：

- Iteration 17 重点回答：
  - 系统基础链路稳不稳
  - 查询类接口快不快
  - WebSocket 链路通不通
- Iteration 18 重点回答：
  - 系统容量档位在哪里
  - 哪组参数是最佳稳定档
  - 哪组参数已经进入超载区

所以两份报告不能简单用“谁的 QPS 更高”来直接比较。  
更准确的理解方式是：

- Iteration 17 更像体检和分项测试
- Iteration 18 更像容量试车和验收测试

本说明把两者整合成一份统一口径文档，讲清楚：

- 每个脚本到底在测什么
- 每个参数到底控制什么
- 每个指标到底代表什么
- 两轮压测的区别是什么
- 哪组性能最好
- 哪组更接近真实业务场景

## 2. 本次测试环境与前提

本次结论基于 2026-04-25 当前系统重新执行，不是引用旧文件。

测试前提：

- PostgreSQL：1 主 2 从
- Redis：3 主 3 从
- RabbitMQ
- Spring Boot 双实例：`app-1`、`app-2`
- Nginx 统一入口
- k6 通过 Docker 容器执行
- 压测流量统一从 `http://nginx` 进入
- 受保护接口通过 `K6_BYPASS_KEY=k6-bypass-20260420` 绕过真实登录流程

这里有一个重要含义：

- 本轮压测更关注后端真实处理能力
- 不把登录、刷新 token、前端浏览器渲染等非核心路径混进结果

也就是说，这份报告是“后端服务容量说明”，不是“端到端用户体验报告”。

## 3. 两轮压测到底有什么区别

## 3.1 Iteration 17 的压测思路

Iteration 17 采用“拆开看”的思路，分别验证三类能力：

1. `perf-fullchain-and-endpoints.js`
   - 用较保守的参数跑 mixed/full-chain
   - 目标是确认核心链路是否稳定
2. `perf-endpoints-common-qps.js`
   - 把查询类接口拆开压
   - 目标是得到一张“查询接口能力清单”
3. `websocket-load-test.js`
   - 单独压 WebSocket 连接和订阅
   - 目标是验证链路是否打通

这种做法的优点：

- 容易定位问题
- 容易看清单接口表现
- 适合做优化前后对照

这种做法的局限：

- 各接口被拆开后，接口之间不会真实争抢资源
- 更适合“诊断系统”，不适合直接当作“生产容量上限”

一句话总结：

- Iteration 17 更偏“分项测试”

## 3.2 Iteration 18 的压测思路

Iteration 18 采用“同一模型下分档位试压”的思路：

1. 仍然使用 `perf-fullchain-and-endpoints.js`
2. 但不是只跑一次，而是跑 A / B / C 三组参数
3. 观察不同负载档下：
   - 总吞吐
   - 失败率
   - 尾延迟
   - 哪个场景先失稳

这种做法的优点：

- 可以直接看到系统稳定区、冲刺区、超载区
- 更适合回答“上线验收应该用哪组参数”
- 更接近真实容量验证

这种做法的局限：

- 结果更依赖场景建模是否合理
- 不像 Iteration 17 那样便于逐项拆解每个接口

一句话总结：

- Iteration 18 更偏“容量测试”

## 3.3 两者最核心的区别

可以把它理解成下面这张对照：

| 维度 | Iteration 17 | Iteration 18 |
|---|---|---|
| 主要目标 | 体检、诊断、基线 | 容量分档、验收、上限 |
| 流量组织方式 | 拆分压测 | 同模型多档位试压 |
| 更适合回答 | 哪个接口快、链路通不通 | 在什么负载下还能稳定 |
| 更适合发现 | 单点热点、链路可用性 | 容量边界、系统失稳点 |
| 更接近真实生产混合流量 | 一般 | 更高 |
| 更适合作为上线验收依据 | 一般 | 更高 |

## 4. 本次用到的脚本与它们在测什么

## 4.1 `k6/perf-fullchain-and-endpoints.js`

这个脚本同时定义了 4 个 scenario：

- `full_chain_mix`
- `endpoint_market_quote`
- `endpoint_portfolio_overview`
- `endpoint_trade_list`

它的压测模型不是“只打一类请求”，而是：

- 一边持续压混合业务
- 一边持续压 3 个关键单接口

因此它模拟的是：

- 系统在真实业务流量存在时
- 同时还承受关键热点接口持续访问

这比只压单接口更接近实际系统运行状态。

### `full_chain_mix` 具体做什么

`full_chain_mix` 每次迭代只会走三类业务中的一种：

- 行情链路：
  - `/api/v1/market/quote/{stockCode}`
  - `/api/v1/market/search`
- 资产链路：
  - `/api/v1/portfolio/overview`
  - `/api/v1/portfolio/positions`
- 交易查询链路：
  - `/api/v1/trade/orders?scope=today`
  - `/api/v1/trade/trades`

注意一个关键点：

- 每次 `full_chain_mix` 迭代，本质上会打 2 个 HTTP 请求
- 所以 `FULL_CHAIN_RPS` 不是最终 HTTP QPS
- `FULL_CHAIN_RPS=650` 时，`full_chain_mix` 理论上大约会带来 `1300 req/s` 的 HTTP 流量

再加上三个单接口场景：

- `QUOTE_RPS`
- `PORTFOLIO_RPS`
- `TRADE_LIST_RPS`

最终总 HTTP QPS 大致为：

`2 * FULL_CHAIN_RPS + QUOTE_RPS + PORTFOLIO_RPS + TRADE_LIST_RPS`

例如 B 组：

- `2 * 650 + 260 + 130 + 130 = 1820`
- 实测总 HTTP QPS 为 `1816.15`

这说明脚本达成率很高，场景模型和实际结果是对得上的。

## 4.2 `k6/perf-endpoints-common-qps.js`

这个脚本覆盖 17 个查询类接口，包括：

- 行情类
- 资产类
- 交易查询类
- 自选
- 通知
- 用户信息

它的定位不是“模拟复杂交易过程”，而是：

- 给查询类接口提供统一的 common-qps 口径
- 看这些接口作为接口族整体时，系统承载能力如何

它更像一张“查询接口压力地图”。

## 4.3 `k6/websocket-load-test.js`

这个脚本主要验证：

- WebSocket 握手是否成功
- STOMP 是否成功连接
- 是否订阅成功并收到消息
- 是否能提取推送时延样本

它的重点是“链路可用性”和“时延采样是否闭环”，不是完整 HTTP 容量测试。

## 5. 参数详细说明

## 5.1 `perf-fullchain-and-endpoints.js` 参数

### 基础参数

- `BASE_URL`
  - 压测目标地址
  - 本次统一为 `http://nginx`
  - 含义：所有流量都先到 Nginx，再转发到应用实例

- `K6_BYPASS_KEY`
  - 压测旁路鉴权密钥
  - 含义：跳过真实登录流程，直接让接口进入业务处理阶段
  - 作用：避免把登录开销、验证码、token 刷新等杂音混入结果

- `ACCEPT_429`
  - 是否把 `429` 当作“允许出现的状态码”
  - 本次为 `true`
  - 这不代表业务成功，只表示脚本不会因为 429 直接把请求视作脚本异常

- `DURATION`
  - 每个场景持续时间
  - 本次统一是 `60s`
  - 含义：这是每档负载的施压时间，不是长稳压测

### full-chain 相关参数

- `FULL_CHAIN_RPS`
  - mixed 场景每秒计划发起的业务迭代数
  - 这是“业务迭代速率”，不是 HTTP 请求数

- `FULL_CHAIN_PRE_ALLOCATED_VUS`
  - 预分配虚拟用户数
  - 含义：启动时先准备好足够的并发 worker，减少运行时扩容影响

- `FULL_CHAIN_MAX_VUS`
  - mixed 场景允许扩到的最大 VU
  - 含义：防止目标到达率太高而没有足够 worker

- `MARKET_RATIO / PORTFOLIO_RATIO / TRADE_RATIO`
  - mixed 场景内三类业务的占比
  - 默认：
    - `MARKET_RATIO=0.65`
    - `PORTFOLIO_RATIO=0.20`
    - `TRADE_RATIO=0.15`
  - 含义：
    - 行情请求在混合业务里最多
    - 资产次之
    - 交易查询最少

这几个比例的意义非常大，因为它们决定了“混合流量像不像真实业务”。

### 三个关键单接口参数

- `QUOTE_RPS`
  - 单独持续压 `/market/quote`

- `PORTFOLIO_RPS`
  - 单独持续压 `/portfolio/overview`

- `TRADE_LIST_RPS`
  - 单独持续压 `/trade/orders?scope=today`

这三个参数的作用不是替代 mixed，而是补充热点接口压力。  
它们让测试结果更容易暴露热点接口瓶颈。

### 其他业务参数

- `RATE_LIMIT_IDENTITY_PREFIX`
  - 生成限流身份前缀，避免所有流量被当成同一个用户

- `K6_USER_ID_BASE / K6_USER_ID_SPAN`
  - 压测用户范围
  - 用来轮转不同 user id，降低单用户缓存或限流造成的偏差

- `STOCK_CODE`
  - 行情默认股票代码
  - 本次脚本默认 `sh600519`

- `PORTFOLIO_PAGE_SIZE`
  - 资产和订单查询的分页大小
  - 这个参数会影响查询开销，不能忽略

- `SEARCH_KEYWORD`
  - 市场搜索关键字
  - 会影响搜索接口命中范围和返回负载

## 5.2 `perf-endpoints-common-qps.js` 参数

### 基础参数

- `BASE_URL`
- `DURATION`
- `K6_BYPASS_KEY`
- `ACCEPT_429`

它们的含义与 full-chain 脚本一致。

### 查询场景参数

- `STOCK_CODE`
  - 行情、K 线查询使用的股票代码

- `SEARCH_KEYWORD`
  - 搜索接口关键字

- `KLINE_FROM / KLINE_TO`
  - K 线时间范围
  - 范围越大，可能越重

- `K6_USER_ID_BASE / K6_USER_ID_SPAN`
  - 用户轮转范围

- `RATE_LIMIT_IDENTITY_PREFIX`
  - 限流身份前缀

### 各接口 RPS 参数

这些参数全部表示“该接口每秒目标到达率”：

- `RPS_MARKET_QUOTE`
- `RPS_MARKET_QUOTES_BATCH`
- `RPS_MARKET_KLINE`
- `RPS_MARKET_SEARCH`
- `RPS_MARKET_LISTED`
- `RPS_MARKET_INDEXES`
- `RPS_MARKET_REALTIME_METRICS`
- `RPS_PORTFOLIO_OVERVIEW`
- `RPS_PORTFOLIO_POSITIONS`
- `RPS_PORTFOLIO_FUND_FLOWS`
- `RPS_PORTFOLIO_EQUITY_CURVE`
- `RPS_TRADE_ORDERS`
- `RPS_TRADE_TRADES`
- `RPS_WATCHLIST_GET`
- `RPS_NOTIFICATIONS_GET`
- `RPS_NOTIFICATIONS_UNREAD_COUNT`
- `RPS_USER_ME`

这里和 Iteration 18 mixed 最大的区别是：

- 这些 RPS 是直接对应接口的
- 没有业务流程组合关系
- 所以更适合做接口族横向比较

## 5.3 `websocket-load-test.js` 参数

- `VUS`
  - 并发连接数
  - 本次为 `1000`

- `DURATION`
  - 压测总时长

- `BASE_URL`
  - setup 阶段调用可见代码接口的 HTTP 地址

- `WS_URL`
  - WebSocket 连接地址
  - 本次为 `ws://nginx/ws/market-native`

- `TARGET_CODE`
  - 订阅的股票代码

- `WS_SESSION_MS`
  - 单连接保持时长

- `WS_HEARTBEAT_MS`
  - 心跳频率

- `K6_BYPASS_KEY`
  - WebSocket 旁路鉴权

这个脚本的关键在于：

- 连接成功不等于时延结果有效
- 收到消息也不等于时延样本闭环

## 6. 指标详细说明

## 6.1 HTTP 吞吐指标

- `http_reqs.rate`
  - HTTP 请求完成速率
  - 可以理解为“总 HTTP QPS”
  - 这是请求级吞吐，不是业务级吞吐

- `http_reqs{scenario:xxx}.rate`
  - 某个场景自己的 HTTP QPS
  - 用来分拆不同 scenario 的实际达成速率

注意：

- `FULL_CHAIN_RPS` 是业务迭代速率
- `http_reqs.rate` 是最终 HTTP 请求速率
- 这两个数字不能直接混为一谈

## 6.2 HTTP 失败率指标

- `http_req_failed.value`
  - HTTP 层失败率
  - 例如 `0.153` 表示 `15.3%`

- `http_req_failed{scenario:xxx}.value`
  - 某个场景自己的失败率

这个指标表示请求有没有失败，不直接表示整个业务流程是否成功。

## 6.3 延迟指标

- `http_req_duration p90/p95/p99`
  - 请求级延迟分位数
  - `p95=11ms` 的含义是：95% 请求在 11ms 内完成

- `full_chain_duration_ms p95/p99`
  - 业务迭代级时延
  - 比单个请求时延更贴近“这次业务流程完成得快不快”

这里要特别注意一个容易误读的点：

- `http_req_duration` 低，不代表整体业务流程一定低
- 因为一次 full-chain 迭代会发多个请求
- 所以评估 mixed 场景时，`full_chain_duration_ms` 更重要

## 6.4 业务成功指标

- `business_success_total`
  - 被脚本判定为成功的业务请求总数

- `business_failure_total`
  - 被脚本判定为失败的业务请求总数

- `full_chain_success_total`
  - 整次 mixed 业务迭代成功次数

- `full_chain_failure_total`
  - 整次 mixed 业务迭代失败次数

这些指标和 `http_req_failed` 的差别在于：

- `http_req_failed` 看的是单个请求
- `full_chain_success_total` 看的是整次业务流程

举例：

- 一次 mixed 迭代里有 2 个请求
- 只要其中 1 个失败，这次迭代就是 `full_chain_failure`

所以 full-chain 失败率往往更接近真实业务可用性。

## 6.5 WebSocket 指标

- `ws_connect_success_rate`
  - 连接握手成功率

- `ws_stomp_connected_total`
  - STOMP 建连成功总数

- `ws_msgs_received`
  - 收到消息总数

- `ws_latency_samples_total`
  - 成功提取出的推送时延样本数

- `ws_push_latency_ms`
  - 推送时延统计

如果：

- `ws_connect_success_rate=100%`
- `ws_stomp_connected_total>0`
- `ws_msgs_received>0`
- 但 `ws_latency_samples_total=0`

那么正确结论只能是：

- 链路通
- 连接成功
- 消息能收到
- 但时延统计不闭环，不能把时延结果当正式结论

## 7. 2026-04-25 当前实测结果

## 7.1 Iteration 17 结果

### 7.1.1 mixed / full-chain

文件：`k6/_current-it17-mixed.json`

结果：

- 总 HTTP QPS：`538.90 req/s`
- HTTP 失败率：`0.00%`
- 总体 `p95 / p99`：`42.58ms / 163.78ms`
- `business_success_total=32404`
- `business_failure_total=0`
- `full_chain_success_total=10801`
- `full_chain_failure_total=0`
- `full_chain_p95=87ms`

解读：

- 这是一个明显偏保守的档位
- 结论重点不是“吞吐高”，而是“跑得稳”
- 它证明 mixed 业务链路在当前系统上是能稳定跑通的

这个结果更像：

- 基础链路确认
- 优化后稳定性确认

不适合直接说成：

- “这就是系统最佳容量”

### 7.1.2 common-qps 多接口查询

文件：`k6/_current-it17-endpoints.json`

结果：

- 总 HTTP QPS：`1067.71 req/s`
- HTTP 失败率：`0.00%`
- 总体 `p95 / p99`：`6.84ms / 27.43ms`
- `endpoint_success_total=64215`
- `endpoint_failure_total=0`

部分场景实际 QPS：

- `market_quote`：`119.72`
- `portfolio_positions`：`89.80`
- `trade_trades`：`79.83`
- `watchlist_get`：`79.83`
- `market_quotes_batch`：`79.83`

解读：

- 查询类接口整体非常稳
- 这份结果更适合回答“查询接口族健康吗”
- 不适合直接拿来代表真实混合业务容量

### 7.1.3 WebSocket

文件：`k6/_current-it17-websocket.json`

结果：

- 并发连接数：`1000`
- `ws_connect_success_rate=100%`
- `ws_stomp_connected_total=1000`
- `ws_msgs_received=1000`
- `ws_latency_samples_total=0`
- `ws_connecting p95=438.92ms`

解读：

- `1000` 个连接能建起来
- STOMP 连接也成功
- 也收到了消息
- 但没有拿到稳定的推送时延样本

所以关于 WebSocket，本轮最严谨的结论只能写成：

- 可连接
- 可订阅
- 可收消息
- 但“高并发推送时延”还没有被正式验证闭环

## 7.2 Iteration 18 结果

## 7.2.1 A 组

文件：`k6/_current-it18-A-seq.json`

参数：

- `FULL_CHAIN_RPS=450`
- `QUOTE_RPS=180`
- `PORTFOLIO_RPS=90`
- `TRADE_LIST_RPS=90`

结果：

- 总 HTTP QPS：`1257.13 req/s`
- HTTP 失败率：`0.17%`
- 总体 `p95 / p99`：`23.13ms / 69.62ms`
- `business_success_total=75474`
- `business_failure_total=130`
- `full_chain_success_total=26958`
- `full_chain_failure_total=43`
- `full_chain_p95=44ms`

解读：

- 相比 Iteration 17 mixed，吞吐明显更高
- 但已经出现少量失败
- 它不算“最优稳定档”，更像“保守高负载档”

### 为什么 A 组总 QPS 会是 `1257` 左右

理论值：

- `2 * 450 + 180 + 90 + 90 = 1260`

实测值：

- `1257.13`

说明：

- k6 达到目标速率的精度较高
- A 组并不是被系统严重压住，而是基本达成目标

## 7.2.2 B 组

文件：`k6/_current-it18-B-seq.json`

参数：

- `FULL_CHAIN_RPS=650`
- `QUOTE_RPS=260`
- `PORTFOLIO_RPS=130`
- `TRADE_LIST_RPS=130`

结果：

- 总 HTTP QPS：`1816.15 req/s`
- HTTP 失败率：`0.00%`
- 总体 `p95 / p99`：`11.31ms / 29.96ms`
- `business_success_total=109204`
- `business_failure_total=0`
- `full_chain_success_total=39001`
- `full_chain_failure_total=0`
- `full_chain_p95=31ms`

分场景结果：

- `full_chain_mix`
  - QPS：`1297.23`
- `endpoint_market_quote`
  - QPS：`259.44`
  - `p95=2.36ms`
  - `p99=6.98ms`
- `endpoint_portfolio_overview`
  - QPS：`129.74`
  - `p95=26.17ms`
  - `p99=48.62ms`
- `endpoint_trade_list`
  - QPS：`129.74`
  - `p95=5.28ms`
  - `p99=11.14ms`

解读：

- B 组吞吐比 A 组高很多
- 但失败率仍然保持 `0`
- `full_chain_p95=31ms`，说明业务流程级尾延迟也健康

它最大的价值在于：

- 不是单纯高吞吐
- 而是“高吞吐 + 零失败 + 可接受尾延迟”同时成立

这就是为什么 B 组应被定义为：

- 当前最佳稳定档
- 当前最适合预发布验收的档位

### 为什么 B 组最关键

理论值：

- `2 * 650 + 260 + 130 + 130 = 1820`

实测值：

- `1816.15`

差距极小，说明：

- 当前系统在这组负载下并没有明显丢失目标到达率
- 线程池、连接池、缓存、MQ、数据库等关键链路在这档压力下是协调的

## 7.2.3 C 组

文件：`k6/_current-it18-C-seq.json`

参数：

- `FULL_CHAIN_RPS=850`
- `QUOTE_RPS=340`
- `PORTFOLIO_RPS=170`
- `TRADE_LIST_RPS=170`

结果：

- 总 HTTP QPS：`2374.56 req/s`
- HTTP 失败率：`15.30%`
- 总体 `p95 / p99`：`11.74ms / 31.98ms`
- `business_success_total=120951`
- `business_failure_total=21854`
- `full_chain_success_total=39101`
- `full_chain_failure_total=11900`
- `full_chain_p95=30ms`

分场景实际 QPS：

- `full_chain_mix`：`1696.09`
- `endpoint_market_quote`：`339.23`
- `endpoint_portfolio_overview`：`169.62`
- `endpoint_trade_list`：`169.62`

分场景失败率：

- `endpoint_market_quote`：`15.19%`
- `endpoint_portfolio_overview`：`34.78%`
- `endpoint_trade_list`：`21.83%`
- `full_chain_mix`：`12.73%`

解读：

- C 组的原始吞吐确实最高
- 但这是“带明显失败”的高吞吐
- 这类数字不能被称为“最佳性能”

这里还有一个非常容易被误读的点：

- C 组的 `http_req_duration p95` 看上去没有很差
- 但失败率已经很高

这说明：

- 单看延迟是不够的
- 必须把失败率一起看

一个失败率已经达到 `15.30%` 的档位，即使延迟不难看，也不能作为稳定容量结论。

所以 C 组正确的定位是：

- 极限冲刺区
- 超载边界附近
- 只适合极限验证，不适合默认运行

## 7.3 Iteration 18 common-qps 结果

文件：`k6/_current-it18-common.json`

结果：

- 总 HTTP QPS：`1347.39 req/s`
- HTTP 失败率：`0.00%`
- 总体 `p95 / p99`：`7.53ms / 24.75ms`
- `endpoint_success_total=81012`
- `endpoint_failure_total=0`

关键接口：

- `market_quote`
  - QPS：`219.56`
  - `p95=2.11ms`
- `portfolio_overview`
  - QPS：`119.75`
  - `p95=21.38ms`
- `portfolio_positions`
  - QPS：`89.83`
  - `p95=21.38ms`
- `trade_orders`
  - QPS：`99.81`
  - `p95=4.99ms`
- `trade_trades`
  - QPS：`79.85`
  - `p95=5.19ms`

解读：

- 查询接口族整体非常稳
- portfolio 相关接口仍然是更重的查询路径
- 但在当前压测口径下仍然处于健康范围

## 8. 两轮压测结果应该怎么对比

## 8.1 如果只问“哪个数字最大”

最大原始吞吐是：

- Iteration 18 C 组：`2374.56 req/s`

但它不是最佳性能，因为：

- HTTP 失败率高达 `15.30%`
- `portfolio_overview` 失败率高达 `34.78%`

所以“数字最大”不等于“性能最好”。

## 8.2 如果问“哪个稳定性能最好”

最佳稳定性能是：

- Iteration 18 B 组

原因：

- 总 HTTP QPS：`1816.15 req/s`
- 失败率：`0`
- `full_chain_p95=31ms`
- 单接口尾延迟仍然健康

这意味着：

- B 组是当前系统在 2026-04-25 实测下的最佳稳定容量点

## 8.3 如果问“哪个更符合实际”

这个问题不能只回答一个名字，要分场景：

### 更符合真实生产混合负载的，是 Iteration 18

原因：

- 它把 mixed 业务和热点单接口同时压
- 它有 A/B/C 三档，能看出容量边界
- 更接近“线上系统在有多种请求同时进入时会发生什么”

如果你的问题是：

- 上线验收该用哪组参数？
- 当前系统稳定容量是多少？
- 哪个档位最适合当默认性能结论？

那么答案就是：

- 看 Iteration 18
- 并且以 B 组为准

### 更符合“分项诊断”和“单项体检”的，是 Iteration 17

原因：

- 它把查询接口拆开看
- 它把 WebSocket 单独看
- 更容易知道“具体哪个模块表现如何”

如果你的问题是：

- 查询接口整体健康吗？
- WebSocket 链路通不通？
- 基础 mixed 场景能不能稳定跑通？

那么答案就是：

- 看 Iteration 17

## 8.4 为什么不能直接说 Iteration 18 一定“全面优于” Iteration 17

因为两者回答的问题不同：

- Iteration 17 解决“有没有问题、问题在哪”
- Iteration 18 解决“能扛多大、推荐跑多大”

所以正确关系不是“谁取代谁”，而是：

- Iteration 17 是诊断口径
- Iteration 18 是容量口径
- 两者结合起来，结论才完整

## 9. 最终结论

截至 2026-04-25，结合两轮压测后，最合理的统一结论如下：

1. 当前最佳稳定容量档位是 Iteration 18 的 `B 组`。
2. 推荐参数为：
   - `FULL_CHAIN_RPS=650`
   - `QUOTE_RPS=260`
   - `PORTFOLIO_RPS=130`
   - `TRADE_LIST_RPS=130`
3. 该档位下关键结果为：
   - 总 HTTP QPS：`1816.15 req/s`
   - HTTP 失败率：`0`
   - `full_chain_p95=31ms`
   - quote-only `p95=2.36ms`
   - portfolio-overview-only `p95=26.17ms`
   - trade-list-only `p95=5.28ms`
4. 如果只看原始吞吐，Iteration 18 `C 组` 最高，但它失败率过高，不属于最佳性能，只属于冲刺极限档。
5. Iteration 17 的价值不是容量上限，而是：
   - mixed 低档稳定性确认
   - 查询接口族健康确认
   - WebSocket 链路可用性确认
6. 在“更符合实际”这个问题上：
   - 如果指真实生产混合负载和上线验收，Iteration 18 `B 组` 更符合实际
   - 如果指分项诊断和模块体检，Iteration 17 更符合实际
7. WebSocket 当前只能确认：
   - `1000` 连接可建立
   - STOMP 可连通
   - 消息可收到
   - 但高并发推送时延样本仍未闭环，因此不能把 WebSocket 时延纳入当前最终性能结论

## 10. 推荐如何使用这两套口径

建议把两套口径分别用于不同阶段：

- 日常优化回归：
  - 先跑 Iteration 17
  - 作用：确认没有明显退化

- 预发布验收：
  - 跑 Iteration 18 B 组
  - 作用：确认稳定容量

- 极限验证：
  - 跑 Iteration 18 C 组
  - 作用：观察超载边界，不作为上线标准

- WebSocket 专项：
  - 单独补时延采样闭环
  - 当前不要把 WS 延迟写进最终性能 SLA

## 11. 本次结果文件

- `k6/_current-it17-mixed.json`
- `k6/_current-it17-endpoints.json`
- `k6/_current-it17-websocket.json`
- `k6/_current-it18-A-seq.json`
- `k6/_current-it18-B-seq.json`
- `k6/_current-it18-C-seq.json`
- `k6/_current-it18-common.json`
