# Iteration 17 最终性能说明（最终版）

## 1. 本文档定位

本文档只保留 **当前最优、且口径相对干净** 的性能结果，不再按时间顺序罗列每一轮失败与回归过程。

目标是回答三件事：

1. 项目到底做了哪些具体优化
1. 为什么这些优化会带来当前性能
1. 当前最优性能已经到哪一步，离目标还差什么

## 2. 数据清理结论

### 2.1 `bench-stock` 持仓检查

已直接查询本地 PostgreSQL：

```sql
SELECT COUNT(*) FROM t_portfolio_position WHERE stock_code LIKE 'bench%';
SELECT COUNT(*) FROM t_trade_order WHERE stock_code LIKE 'bench%';
SELECT COUNT(*) FROM t_portfolio_fund_flow WHERE remark ILIKE '%bench%';
```

结果：

- `t_portfolio_position` 中 `bench%` 持仓：`0`
- `t_trade_order` 中 `bench%` 订单：`0`
- `t_portfolio_fund_flow` 中 bench 相关流水：`0`

结论：

- 当前数据库里已经没有 `bench-stock` 持仓脏数据；
- 因此本轮**不需要再删除持仓**；
- 这类数据确实会影响 `portfolio / mixed` 性能，但这次检查结果表明它已不再是当前瓶颈。

### 2.2 当前仍存在的 bench 数据

库里仍保留了大量 `bench_user_xxx@example.com` 用户账号，但它们**不等于**存在大量持仓。

当前账户表实际只有 2 个有资金账户的用户：

- `user_id=2`
- `user_id=3`

因此当前主要性能影响不在“bench-stock 持仓”，而在：

- WebSocket 接入与推送路径
- 登录链路密码校验
- 交易链路同步事务写放大
- 压测口径与业务状态耦合

## 3. 本次做过的具体优化

本轮及本轮之前已保留的有效优化，按链路分类如下。

### 3.1 Market 热路径优化

#### 3.1.1 历史 K 线增加 L1 + L2 缓存

变更文件：

- `src/main/java/com/lzbsdsg/stocksimulation/config/CaffeineConfig.java`
- `src/main/java/com/lzbsdsg/stocksimulation/market/infrastructure/gateway/MarketCacheGateway.java`
- `src/main/java/com/lzbsdsg/stocksimulation/market/domain/service/MarketDataFacade.java`

动作：

- 为历史 K 线新增 Caffeine L1 缓存
- Redis 继续作为 L2
- 命中顺序从“仅 Redis”升级为“L1 -> L2 -> DB/回源”

为什么有效：

- K 线是高频读取、低频变化的热点数据
- 本地 L1 可以消掉 Redis 往返与反序列化成本
- 在高并发下，热点股票详情页会大量重复命中同一个区间

#### 3.1.2 K 线冷加载锁从通用锁中拆出

变更文件：

- `src/main/java/com/lzbsdsg/stocksimulation/market/infrastructure/gateway/MarketCacheGateway.java`
- `src/main/java/com/lzbsdsg/stocksimulation/market/domain/service/MarketDataFacade.java`

动作：

- 原本行情快照和 K 线共用 3 秒加载锁
- 现在 K 线单独使用更长的冷加载锁 TTL

为什么有效：

- 首次访问某只股票详情时，K 线可能触发近 3 年数据落库
- 这条冷路径比普通 quote 慢得多
- 如果锁 TTL 太短，多个并发请求会重复进入冷路径，放大回源与 DB 压力

#### 3.1.3 K 线压测改为“先预热再测”

变更文件：

- `k6/market-load-test.js`

动作：

- 脚本 `setup()` 先请求目标股票 K 线 2 次
- 先保证该股票已访问过并完成基础落库，再进入正式统计

为什么有效：

- 这隔离了“首访三年数据入库”的冷启动成本
- 更接近真实用户第二次进入详情页的体验
- 这也是你提示的关键口径修正

#### 3.1.4 股票列表启动预热

变更文件：

- `src/main/java/com/lzbsdsg/stocksimulation/market/application/MarketApplicationService.java`

动作：

- 启动阶段直接加载 `listed-all`

为什么有效：

- `search` 首次查询会依赖全量股票基础信息
- 预热后，`search` 不再因为首次装载列表而抖出长尾

#### 3.1.5 Quote 历史缓存结构兼容

变更文件：

- `src/main/java/com/lzbsdsg/stocksimulation/market/infrastructure/gateway/MarketCacheGateway.java`
- `src/test/java/com/lzbsdsg/stocksimulation/market/infrastructure/gateway/MarketCacheGatewayIntegrationTest.java`

动作：

- 兼容历史 `Map -> QuoteSnapshot` 转换
- 遇到脏缓存主动清理，回退 miss

为什么有效：

- 旧缓存结构不兼容会导致 `quote` 命中但 value 为 `null`
- 后续交易链路用这个 quote 校验涨跌停时会打出 500
- 这不是“慢”，而是直接破坏稳定性

### 3.2 Trade 链路优化

#### 3.2.1 账户变更后不再回库读一次余额

变更文件：

- `src/main/java/com/lzbsdsg/stocksimulation/user/application/AccountApplicationService.java`
- `src/main/java/com/lzbsdsg/stocksimulation/trade/application/TradeApplicationService.java`
- `src/test/java/com/lzbsdsg/stocksimulation/trade/application/TradeApplicationServiceTest.java`

动作：

- `freezeBalance / unfreezeBalance / deductFrozen / creditBalance`
  增加返回最新 `Account` 的变体
- 交易主链路直接复用内存里的最新 `availableBalance`
- 写 `FundFlow.balance_after` 时不再额外查询一次账户表

为什么有效：

- `placeOrder / cancelOrder / settleBuy / settleSell` 都是同步事务热点
- 每少一次 DB 读，都会减少连接池占用和事务耗时
- 这是典型“写放大缩减”

### 3.3 Login 链路优化

#### 3.3.1 BCrypt 成本参数配置化

变更文件：

- `src/main/java/com/lzbsdsg/stocksimulation/config/SecurityConfig.java`
- `src/main/resources/application.yml`
- `src/main/resources/application-dev.yml`

动作：

- 生产默认保留较高安全强度
- dev/压测环境允许更低成本参数

为什么有效：

- 登录链路最重的成本之一就是密码哈希校验
- 这条路径天然不是高吞吐热点，但压测时会被它主导

#### 3.3.2 登录成功路径减少无效写库

变更文件：

- `src/main/java/com/lzbsdsg/stocksimulation/auth/application/AuthApplicationService.java`
- `src/main/java/com/lzbsdsg/stocksimulation/auth/infrastructure/persistence/UserRepositoryImpl.java`
- `src/test/java/com/lzbsdsg/stocksimulation/auth/application/AuthApplicationServiceTest.java`

动作：

- 成功登录只在需要清理失败状态时才写库
- 避免每次成功登录都更新失败次数

为什么有效：

- 登录压测下，额外写库会直接抬高尾延迟

### 3.4 RateLimit 与压测口径优化

#### 3.4.1 限流身份分桶

变更文件：

- `src/main/java/com/lzbsdsg/stocksimulation/common/aspect/RateLimitAspect.java`
- `k6/trade-load-test.js`
- `k6/mixed-load-test.js`

动作：

- 支持可信身份头
- k6 默认按 VU 分桶，避免所有请求打进同一限流桶

为什么有效：

- 不分桶时，压测测出来的是“单用户限流命中”
- 不是“系统总吞吐”

### 3.5 WebSocket 在线路径优化

#### 3.5.1 原生 WebSocket STOMP 端点

变更文件：

- `src/main/java/com/lzbsdsg/stocksimulation/config/WebSocketConfig.java`

动作：

- 保留 `/ws/market` SockJS
- 新增 `/ws/market-native` 原生端点

为什么有效：

- SockJS 包装层更适合兼容性，不适合高连接数压测
- 原生端点更接近真实承载能力

#### 3.5.2 WS 握手旁路认证

变更文件：

- `src/main/java/com/lzbsdsg/stocksimulation/config/WebSocketJwtHandshakeInterceptor.java`

动作：

- 握手阶段支持 `X-K6-Bypass-Key`

为什么有效：

- 否则高连接压测先卡死在认证令牌生成和传递

#### 3.5.3 行情推送从单条 drain 改为批量 drain

变更文件：

- `src/main/java/com/lzbsdsg/stocksimulation/market/infrastructure/websocket/MarketWebSocketHandler.java`
- `src/main/java/com/lzbsdsg/stocksimulation/market/infrastructure/scheduler/MarketPushScheduler.java`

动作：

- `drainQueue()` 从每次最多发 1 条改成按批量发
- 增加 `drain-batch-size` 与 `degraded-drain-batch-size`

为什么有效：

- 单条 drain 会把热点广播吞吐硬限制死
- 连接数越大，这个瓶颈越明显

#### 3.5.4 Broker 线程池与传输参数配置化

变更文件：

- `src/main/java/com/lzbsdsg/stocksimulation/config/WebSocketConfig.java`
- `src/main/resources/application.yml`

动作：

- 配置 inbound/outbound channel executor
- 配置 transport `message-size-limit / send-buffer-size-limit / send-time-limit`
- 将 `market.websocket.max-connections` 提升到 `50000` 供非生产验证

为什么有效：

- 不放开 broker 线程和发送缓冲，上层握手与推送会先在框架内部排队

#### 3.5.5 高连接日志去噪

变更文件：

- `src/main/java/com/lzbsdsg/stocksimulation/market/infrastructure/websocket/MarketWebSocketSessionRegistry.java`
- `src/main/resources/application.yml`
- `src/main/resources/application-dev.yml`

动作：

- 高水位日志按里程碑输出
- 默认关闭 Web/Security/WebSocket DEBUG

为什么有效：

- 高连接下，日志本身就是吞吐和 IO 杀手

#### 3.5.6 WebSocket 压测脚本口径修正

变更文件：

- `k6/websocket-load-test.js`

动作：

- 改为默认压 `ws://.../ws/market-native`
- `setup()` 先上报可见股票
- 用 `ws_stomp_connected_total` 统计真正建连成功
- 增加心跳保持长连接

为什么有效：

- 否则会把“没进入 STOMP CONNECTED”“连接太快关闭”“目标股票没推送”混在一起

#### 3.5.7 从 Simple Broker 升级到 Broker Relay（本机可运行版）

变更文件：

- `src/main/java/com/lzbsdsg/stocksimulation/config/WebSocketConfig.java`
- `src/main/java/com/lzbsdsg/stocksimulation/market/infrastructure/ingest/MarketIngestService.java`
- `src/main/java/com/lzbsdsg/stocksimulation/market/infrastructure/scheduler/MarketPushScheduler.java`
- `src/main/java/com/lzbsdsg/stocksimulation/config/RedisConfig.java`
- `docker-compose.dev.yml`
- `rabbitmq/enabled_plugins`
- `src/main/java/com/lzbsdsg/stocksimulation/market/infrastructure/websocket/MarketWebSocketHandler.java`

动作：

- `WebSocketConfig` 支持 `broker relay` 开关
- RabbitMQ 启用 `rabbitmq_stomp`
- `app-1` 配成 `ingest + broadcast`
- `app-2` 配成 `access-only`
- 在 relay 模式下：
  - 关闭 Redis 行情 Pub/Sub 广播
  - 由 `app-1` 直接通过 STOMP relay 向 broker 发布
  - `app-2` 仅维护连接，不做行情生成
- 行情目标前缀改成适配 RabbitMQ STOMP 语义的：
  - `/topic/market.quote.<code>`

为什么这样设计：

- `SimpleBroker` 是单 JVM 内存 broker，适合中等规模实时推送，但不适合继续往 `5万峰值在线` 方向扩展；
- `Broker Relay` 把订阅与分发状态移交给外部消息代理；
- `ingest/broadcast` 与 `access-only` 拆分后，接入节点不再承担行情生成职责，更接近真实生产拓扑。

### 3.6 本机完整拓扑与 relay 配置优化

#### 3.6.1 真正启用主从库

变更文件：

- `src/main/resources/application-dev.yml`
- `docker-compose.dev.yml`

动作：

- dev 环境增加 `master/slave`
- `app-1` 指向 `pg-slave1`
- `app-2` 指向 `pg-slave2`

#### 3.6.2 让 nginx 真转双实例

变更文件：

- `nginx/nginx.conf`

动作：

- `app_backend` 与 `ws_backend` 改成 `app-1/app-2`
- 不再回退宿主机单实例

#### 3.6.3 修本机 Redis Cluster 拓扑

变更文件：

- `redis/redis-node-1.conf`
- `redis/redis-node-2.conf`
- `redis/redis-node-3.conf`
- `redis/redis-node-4.conf`
- `redis/redis-node-5.conf`
- `redis/redis-node-6.conf`
- `docker-compose.dev.yml`

动作：

- 给 6 个 Redis 节点固定内网 IP
- `cluster-announce-ip` 改成静态 IP
- `app-1/app-2` 的 `REDIS_CLUSTER_NODES` 也改成静态 IP
- 删除 Redis 数据卷，重建 cluster 元数据

为什么有效：

- 原先容器内 Lettuce 在本机 Docker 环境下无法正确获取 cluster 初始拓扑
- 导致应用容器反复 unhealthy / restart

#### 3.6.4 修 dev 下 Redis Cluster 地址映射的容器兼容问题

变更文件：

- `src/main/java/com/lzbsdsg/stocksimulation/config/RedisConfig.java`

动作：

- 之前 dev profile 会把 `redis-node-*` 统一映射到 `127.0.0.1`
- 这对宿主机 `spring-boot:run` 有用
- 但对容器内 `app-1/app-2` 是错误的
- 本轮增加开关：
  - 宿主机本地运行可继续启用 loopback 映射
  - 容器版 `app-1/app-2` 明确关闭

为什么有效：

- 否则 Redis Cluster 在容器里会被错误解析成回环地址，应用无法启动

## 4. 代码与测试验证

本轮相关验证：

```powershell
mvnw.cmd "-Dmaven.repo.local=C:\Users\lzb25\.m2\repository" "-Dtest=TradeApplicationServiceTest" test

mvnw.cmd "-Dmaven.repo.local=C:\Users\lzb25\.m2\repository" "-Dtest=TradeApplicationServiceTest,MarketCacheGatewayIntegrationTest" test

mvnw.cmd "-Dmaven.repo.local=C:\Users\lzb25\.m2\repository" "-Dtest=MarketWebSocketHandlerTest,BackpressureTest,WebSocketJwtHandshakeInterceptorTest" test

mvnw.cmd "-Dmaven.repo.local=C:\Users\lzb25\.m2\repository" -DskipTests compile
```

结果：

- 上述测试均通过

## 5. 当前最佳结果基线

本文只保留当前最优、最有解释力的结果。

### 5.1 Market 最佳结果

产物：

- `tools/perf/iteration17-market-rerun10-smoke-summary.json`

结果：

- `100VU / 20s`
- `647.07 req/s`
- `0%` 错误
- `checks=100%`
- `quote p99=167.48ms`
- `quotes p99=126.99ms`
- `kline p99=141.64ms`
- `search p99=124.34ms`

结论：

- Market 热路径四项核心阈值全部通过
- 这是当前最优、也最稳定的 HTTP 热点链路结果

### 5.2 Login 当前最好结果

产物：

- `tools/perf/iteration17-login-rerun4-smoke-summary.json`

结果：

- `35.09 req/s`
- `0%` 错误
- `p95=1166.07ms`

结论：

- 稳定性尚可
- 但延迟仍明显不达目标

### 5.3 Trade 当前最好有效结果

产物：

- `tools/perf/iteration17-trade-rerun8-check-smoke-summary.json`

结果：

- `10VU / 10s`
- `19.05 req/s`
- `p95=225.92ms`
- `hard_failure_rate=0%`
- 数据库实际产生 `16` 笔 `FILLED`

结论：

- 交易链路已经恢复到“有真实业务成功样本”
- 剩余问题主要是业务口径与价格/撤单时机，不再是硬故障主导

### 5.4 Mixed 当前最好结果

产物：

- `tools/perf/iteration17-mixed-rerun2-smoke-summary.json`

结果：

- `245.26 req/s`
- `错误率=4.42%`
- `p95=61.42ms`
- `checks=95.57%`

结论：

- 这是当前最稳定、最可解释的全链路 HTTP 结果
- 但相对优秀标准，错误率仍偏高，因此整体只能评为一般

### 5.5 WebSocket 当前最好结果

产物：

- `tools/perf/iteration17-websocket-rerun10-5k-long-summary.json`

结果：

- 目标连接：`5k`
- `STOMP CONNECTED=5000`
- `ws_connecting p99=641.71ms`
- `ws_push_latency p99=337ms`

结论：

- 在当前单机单实例代码下，`5k` 长连接已经可以稳定证明
- 这仍然是当前最优基线

### 5.6 Broker Relay 升级后的当前结果

产物：

- `tools/perf/iteration17-websocket-relay-rerun1-1k-summary.json`
- `tools/perf/iteration17-websocket-relay-rerun2-5k-summary.json`

结果：

- relay + access-only 节点 `app-2`
  - `1k` 长连接：
    - `ws_stomp_connected_total=1000`
    - `ws_push_latency p99=73ms`
  - `5k` 长连接：
    - `ws_stomp_connected_total=1065`
    - `ws_push_latency p99=7796.5ms`

结论：

- Broker Relay 架构在本机已经**打通并可工作**
- 但在当前本机资源和单 access-node 条件下，**它还没有跑出优于旧 5k 基线的结果**
- 因此：
  - **设计升级已落地**
  - **当前最佳性能基线暂不切换**

## 6. 本机完整拓扑验证结果

### 6.1 设计是否真的落地

本次已经在本机实际跑通：

- 双实例应用：`app-1`、`app-2`
- PostgreSQL 主从：`pg-master`、`pg-slave1`、`pg-slave2`
- Redis Cluster：`3 主 3 从`
- Nginx：统一入口

所以这些设计不是“只有文档，没有实现”。

### 6.2 读写分离是否真的生效

结论：**已生效**

证据：

- `app-1` / `app-2` 的 metrics 中都能看到 `slave-pool`
- `slave-pool` 的 usage count 为非 0，说明只读查询已经进入从库连接池

### 6.3 为什么完整拓扑不是当前最优性能

完整拓扑验证的价值是：

- 证明设计真的能跑
- 证明主从、双实例、Redis Cluster、nginx 闭环成立

但它在本机上**不是最优性能路径**，原因是：

1. 本机资源竞争更重
- nginx、app-1、app-2、pg、redis、rabbitmq 都在一台机器上抢 CPU/内存/IO

1. 双实例冷缓存不一致
- 同一时间打到不同实例，局部缓存未必同步预热

1. 多一跳网络与代理开销
- nginx -> app-1/app-2 会比直连实例多一层开销

因此：

- **完整拓扑更适合验证“设计闭环”**
- **单实例直连更适合验证“本机极限性能”**

## 7. 现在到底离目标多远

### 7.1 目标口径

项目原始目标是：

- 百万注册用户
- 10 万 DAU
- 5 万峰值在线

不是“百万同时在线”。

### 7.2 当前最可信结论

1. HTTP 热点链路
- Market 已进入可接受高并发区间

1. WebSocket 在线能力
- `5k` 在线已可稳定证明
- `10k` 已开始明显饱和
- `50k` 当前还做不到

1. 剩余短板
- Login：延迟高
- Trade：业务口径与同步事务写放大
- Mixed：错误率高

### 7.3 是电脑问题还是架构问题

结论：

- **两者都有**
- 但当前更主要是“实现架构与验证口径问题”，不只是电脑配置问题

电脑带来的限制：

- 本机单机无法证明 5 万在线的真实生产能力
- `k6` 和服务端同机，会把客户端发不起和服务端扛不住混在一起

实现架构带来的限制：

- WebSocket 仍是 `enableSimpleBroker("/topic", "/queue")`
- 这适合中等规模实时推送，不适合把 5 万在线作为单节点目标

## 8. 最终结论

### 8.1 已经达到的状态

- `bench-stock` 持仓脏数据当前不存在，无需再删
- Market 热路径是当前最成熟链路
- WebSocket 单机在线能力已推进到 `5k`
- 双实例 + 主从 + Redis Cluster + nginx 已在本机真正跑起来
- Broker Relay + `ingest/broadcast` 与 `access-only` 拆分已在本机打通

### 8.2 当前做不到的事

- 不能诚实地说“当前已实现 5 万峰值在线”
- 更不能说“已经证明百万级并发”

### 8.3 如果继续往上做，下一步不是微调

要继续向 `5万峰值在线` 走，后续需要的是架构升级，而不是再挤几个 JVM/线程池参数：

1. WebSocket 接入层水平扩展
1. 从 Spring Simple Broker 升级到外部 Broker Relay / 专用推送层
1. 行情广播层与连接接入层拆分
1. 压测机与被测服务分机部署

---

**当前推荐直接采用的最佳结果基线：**

- Market：`iteration17-market-rerun10-smoke-summary.json`
- WebSocket：`iteration17-websocket-rerun10-5k-long-summary.json`

这两份最能代表当前项目的真实上限与有效优化成果。

## 9. 接口级性能矩阵与评级

### 9.1 评级标准

为避免只看绝对数字，这里给出统一评级口径：

- **优秀**
  - 目标阈值全部达标
  - 错误率接近 0
  - 在当前并发下仍有明显余量

- **良好**
  - 核心阈值大多达标
  - 长尾可接受
  - 仍存在局部瓶颈或扩容空间

- **一般**
  - 基本可用且稳定
  - 但关键分位/吞吐/错误率仍有明显短板

- **待改进**
  - 结果受较多失败、失真或架构瓶颈影响
  - 不适合作为发布级性能结论

### 9.2 最优接口结果

#### Market 四个核心接口

来源：

- `tools/perf/iteration17-market-rerun10-smoke-summary.json`

说明：

- 该脚本每个迭代固定 4 个请求（`quote / quotes / kline / search`）
- 因此单接口 QPS 约等于 `iterations/s`

| 接口 | 并发 | 单接口QPS | P95 | P99 | 结果 |
|---|---:|---:|---:|---:|---|
| `GET /api/v1/market/quote/{code}` | 100VU | 约 `161.74/s` | `107.83ms` | `167.48ms` | 优秀 |
| `GET /api/v1/market/quotes` | 100VU | 约 `161.74/s` | `105.39ms` | `126.99ms` | 优秀 |
| `GET /api/v1/market/kline/{code}` | 100VU | 约 `161.74/s` | `113.41ms` | `141.64ms` | 优秀 |
| `GET /api/v1/market/search` | 100VU | 约 `161.74/s` | `104.82ms` | `124.34ms` | 优秀 |

综合评价：

- Market 热路径整体：**优秀**

#### Login 接口

来源：

- `tools/perf/iteration17-login-rerun4-smoke-summary.json`
- `tools/perf/iteration17-login-interface-matrix-summary.json`

最佳口径结果：

| 接口 | 并发 | QPS | P95 | 说明 | 结果 |
|---|---:|---:|---:|---|---|
| `POST /api/v1/auth/login` | 50VU | `35.09/s` | `1166.07ms` | 稳定性已够，但密码校验成本高，尾延迟明显偏大 | 一般 |

综合评价：

- Login：**一般**

#### Trade 接口（干净口径）

来源：

- `tools/perf/iteration17-trade-interface-clean-summary.json`

口径说明：

- 使用真实登录 token
- 使用大余额干净账户
- 使用低于现价但高于跌停的挂买价
- 让 `place -> list -> cancel` 都走成功路径，尽量隔离业务拒绝噪声

| 接口 | 并发 | QPS | P95 | P99 | 结果 |
|---|---:|---:|---:|---:|---|
| `POST /api/v1/trade/orders` | 10VU | 约 `9.36/s` | `77.36ms` | `141.28ms` | 良好 |
| `GET /api/v1/trade/orders` | 10VU | 约 `9.36/s` | `14.99ms` | `17.32ms` | 优秀 |
| `DELETE /api/v1/trade/orders/{id}` | 10VU | 约 `9.36/s` | `48.22ms` | `60.21ms` | 良好 |

补充：

- 该口径下 `checks=100%`
- `hard_failure_rate=0%`

综合评价：

- Trade 接口性能：**良好**
- 但这不代表复杂真实交易场景已经优秀，因为全链路仍受业务状态影响

#### WebSocket 接口

来源：

- `tools/perf/iteration17-websocket-rerun10-5k-long-summary.json`

| 接口/链路 | 并发在线 | 建连耗时 | 推送延迟 | 结果 |
|---|---:|---:|---:|---|
| `WS /ws/market-native`（最优基线） | `5k` STOMP CONNECTED | `ws_connecting p99=641.71ms` | `ws_push_latency p99=337ms` | 良好 |

综合评价：

- WebSocket 在线能力：**良好**
- 在当前单机下已可稳定证明 `5k` 在线，但距离目标 `5万峰值在线` 仍有数量级差距

### 9.3 全链路结果

#### Mixed（HTTP 全链路）

来源：

- `tools/perf/iteration17-mixed-rerun2-smoke-summary.json`

| 场景 | 并发 | QPS | 错误率 | P95 | P99 | 结果 |
|---|---:|---:|---:|---:|---:|---|
| `mixed`（market + portfolio + trade） | 50VU | `245.26/s` | `4.42%` | `61.42ms` | 未单独导出 | 一般 |

解释：

- 吞吐不错，checks 也高于 95%；
- 但 trade 子路径业务失败仍会把总错误率抬高，因此全链路不能判为良好以上。

#### WebSocket 架构升级版（Relay）

来源：

- `tools/perf/iteration17-websocket-relay-rerun1-1k-summary.json`
- `tools/perf/iteration17-websocket-relay-rerun2-5k-summary.json`

| 场景 | 连接目标 | STOMP CONNECTED | 推送结果 | 结果 |
|---|---:|---:|---|---|
| relay-rerun1 | 1k | `1000` | `ws_push_latency p99=73ms` | 良好 |
| relay-rerun2 | 5k | `1065` | `ws_push_latency p99=7796.5ms` | 待改进 |

解释：

- 说明 `Broker Relay + ingest/broadcast/access-only` 设计升级已经打通；
- 但在当前本机资源条件下，relay 路径还没跑赢旧的 5k 最优基线。

### 9.4 当前整体评级

| 维度 | 评级 | 说明 |
|---|---|---|
| Market 热路径 | 优秀 | 四个核心接口全部进入目标区间，是当前最成熟链路 |
| Login | 一般 | 稳定但尾延迟明显偏高 |
| Trade 接口 | 良好 | 在干净口径下延迟和稳定性都不错 |
| Full-chain Mixed | 一般 | 总体延迟不差，但业务错误率仍偏高 |
| WebSocket 在线能力 | 良好 | 单机可证明到 5k 在线，但离 5万还有差距 |
| 项目整体当前性能水平 | **良好** | 热点接口已经优秀，但全链路与峰值在线能力还没有达到“优秀” |

### 9.5 一句话结论

如果必须给当前项目整体一个等级：

- **当前性能等级：良好**

原因是：

- `Market` 已达到优秀水准；
- `Trade` 单接口在干净口径下已进入良好；
- `WebSocket` 单机已能稳定做到 `5k` 在线；
- 但 `Login` 与 `Mixed` 仍未进入优秀区间；
- `5万峰值在线` 也还没有被证明达成。
