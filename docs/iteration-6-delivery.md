# Iteration 6 交付说明（行情模块 WebSocket + 背压）

## 1. 迭代目标

基于 `docs/doc-D-dev-roadmap.md` 的 Iteration 6 要求，完成以下能力并可验收：

- WebSocket(STOMP over SockJS) 实时行情推送链路
- JWT 鉴权握手与连接注册表管理
- 连接上限控制（10000/实例）
- 背压控制（队列丢弃、延迟降级、大包暂停）
- 指标采集（连接数、推送耗时、背压丢弃）
- 配套测试、性能脚本与验收操作方法

## 2. 完成任务清单

### 2.1 WebSocket 鉴权与连接管理

- 完成 `WebSocketJwtHandshakeInterceptor`：
  - 握手前校验 `Authorization: Bearer <token>` 或 `access_token` 查询参数
  - Token 非法时返回 `401`
  - 连接数超过上限时返回 `503`

- 完成 `WebSocketStompInterceptor`：
  - CONNECT 阶段绑定用户 Principal
  - 注册会话到连接注册表
  - DISCONNECT 阶段清理会话

- 完成 `MarketWebSocketSessionListener`：
  - 监听异常断连事件并兜底清理会话，防止注册表残留

- 更新 `WebSocketConfig`：
  - endpoint `/ws/market`
  - 接入握手拦截器 + 入站 STOMP 拦截器

### 2.2 背压与推送调度

- 完成 `MarketWebSocketHandler`：
  - 连接注册表：`ConcurrentHashMap<userId, Set<sessionId>>`
  - 连接计数器：`ws_active_connections` Gauge
  - 推送缓冲队列：深度上限默认 100
  - 队列满时丢弃最旧消息并计数 `ws_push_dropped_total`
  - 推送耗时指标：`ws_push_duration_seconds`
  - 推送延迟超过阈值（默认 5s）自动进入降级模式（10s 推送周期）
  - 单条消息序列化后超过 64KB 直接丢弃（暂停发送该条）
  - 连接数超过 8000 输出高水位告警日志：`ws.connection.high`

- 完成 `MarketPushScheduler`：
  - 按固定频率派发待推送队列（默认 500ms）
  - 实际推送节流和降级策略由 `MarketWebSocketHandler` 统一控制

### 2.3 配置与监控

- `application.yml` 新增 `market.websocket.*` 配置项：
  - `max-connections`
  - `backpressure-queue-depth`
  - `payload-bytes-limit`
  - `push-interval-ms`
  - `degraded-push-interval-ms`
  - `degrade-lag-threshold-ms`
  - `dispatch-interval-ms`

- `prometheus/alert-rules.yml` 调整/新增：
  - `HighWsConnections` 使用 `ws_active_connections > 8000`
  - 新增 `WsPushDroppedHigh`（`rate(ws_push_dropped_total[5m]) > 10`）

### 2.4 压测脚本

- 完成 `k6/websocket-load-test.js`：
  - 建立 WebSocket 连接
  - 发送 STOMP CONNECT / SUBSCRIBE 帧
  - 订阅 `/topic/market/quote/{code}`
  - 采集推送延迟指标 `ws_push_latency_ms`
  - 阈值：`p(99) < 500ms`

## 3. 文件变更清单

### 3.1 新增文件

- `src/main/java/com/lzbsdsg/stocksimulation/config/WebSocketJwtHandshakeInterceptor.java`
- `src/main/java/com/lzbsdsg/stocksimulation/config/WebSocketStompInterceptor.java`
- `src/main/java/com/lzbsdsg/stocksimulation/market/infrastructure/websocket/MarketWebSocketSessionListener.java`
- `src/test/java/com/lzbsdsg/stocksimulation/market/infrastructure/websocket/MarketWebSocketHandlerTest.java`
- `src/test/java/com/lzbsdsg/stocksimulation/market/infrastructure/websocket/BackpressureTest.java`
- `docs/iteration-6-delivery.md`

### 3.2 修改文件

- `src/main/java/com/lzbsdsg/stocksimulation/config/WebSocketConfig.java`
- `src/main/java/com/lzbsdsg/stocksimulation/market/infrastructure/websocket/MarketWebSocketHandler.java`
- `src/main/java/com/lzbsdsg/stocksimulation/market/infrastructure/scheduler/MarketPushScheduler.java`
- `src/main/resources/application.yml`
- `src/test/java/com/lzbsdsg/stocksimulation/market/infrastructure/ingest/MarketPubSubListenerTest.java`
- `k6/websocket-load-test.js`
- `prometheus/alert-rules.yml`

## 4. 测试命令与结果

### 4.1 已执行测试（本轮）

在项目根目录执行：

```cmd
cd /d d:\StockSimulation\stock-simulation
mvnw.cmd "-Dtest=MarketWebSocketHandlerTest,BackpressureTest,MarketPubSubListenerTest" test
```

结果：

- 通过：12
- 失败：0

### 4.2 建议补充回归

```cmd
cd /d d:\StockSimulation\stock-simulation
mvnw.cmd "-Dtest=MarketIngestServiceTest,MarketDataFacadeTest,MarketControllerApiTest" test
```

## 5. 验收标准（不只测试类通过）

### 5.1 功能可用性标准

1. WebSocket 可连通且鉴权生效
- 带合法 JWT 可连接 `/ws/market`
- 无 Token 或非法 Token 返回 401

1. 连接注册表与清理正确
- 建连后 `ws_active_connections` 增加
- 客户端断开后 `ws_active_connections` 回落

1. 推送行为符合节流策略
- 正常模式下按 3s 推送周期派发
- 慢消费者触发降级后切换到 10s 推送周期

1. 上限保护正确
- 超过 10000 连接后新连接返回 503

1. 背压策略可观测
- 队列溢出时 `ws_push_dropped_total` 增长
- payload > 64KB 时消息被丢弃且计数增长

### 5.2 性能标准

1. 阶段性性能目标（Iteration 6 / M2 可看盘）
- 单实例 200 VU、持续 2 分钟：`ws connect success >= 95%`
- 单实例 1000 VU、持续 2 分钟：`ws connect success >= 90%`
- 长连接模式（建议 `WS_SESSION_MS >= 60000`）下，`ws_connecting p(99) < 1000ms`

1. WebSocket 延迟指标（有效口径）
- 服务端推送 payload 包含 `wsPushTsMillis`（毫秒时间戳）
- k6 以 `wsPushTsMillis` 计算 `ws_push_latency_ms`
- k6 输出 `ws_latency_samples_total`，要求 `count > 0`（确保延迟样本有效）
- 验收阈值：`ws_push_latency_ms p(99) < 500ms`

1. 指标与告警
- `ws_active_connections`、`ws_push_duration_seconds`、`ws_push_dropped_total` 在 Prometheus 可见
- 连接高水位和背压丢弃高水位告警可触发

## 6. 具体验收方法（可直接执行）

### 6.1 启动服务

```cmd
cd /d d:\StockSimulation\stock-simulation
mvnw.cmd spring-boot:run
```

### 6.2 获取 JWT（登录接口）

推荐使用 Swagger 调试登录并获取 token：

1. 打开 `http://localhost:8080/swagger-ui.html`
2. 执行 `/api/v1/auth/login`，复制返回的 `accessToken`
3. 点击 Swagger 右上角 `Authorize`，填入 `Bearer <accessToken>`
4. 后续 REST 接口直接在 Swagger 内调试

后续 WebSocket 连接使用：`Authorization: Bearer <accessToken>`

### 6.3 WebSocket 接口联调验收

说明：Swagger 主要用于 HTTP/REST 调试，不直接承载 STOMP WebSocket 会话。

建议流程：

1. 先在 Swagger 完成登录与行情 REST 接口验收（如 `/api/v1/market/quote/{stockCode}`、`/api/v1/market/quotes`）
2. 再使用前端页面或支持 STOMP 的客户端连接 `ws://localhost:<PORT>/ws/market`（若客户端不支持 SockJS 协议协商，可尝试 `ws://localhost:<PORT>/ws/market/websocket`）
3. 发送 CONNECT 帧并订阅：`/topic/market/quote/bj920000`
4. 观察是否持续收到行情消息
5. 断开连接后确认指标回收

### 6.4 指标验收

```cmd
curl "http://localhost:8080/actuator/prometheus" | findstr /i "ws_active_connections ws_push_duration_seconds ws_push_dropped_total"
```

预期：三个指标均可查询。

### 6.5 性能验收（k6）

先跑 20s 烟测：

```cmd
k6 run --vus 1 --duration 20s -e WS_URL=ws://localhost:8080/ws/market/websocket -e WS_TOKEN=<ACCESS_TOKEN> -e TARGET_CODE=bj920000 -e WS_SESSION_MS=15000 k6/websocket-load-test.js
```

或使用一键命令自动选择活跃代码（从最近日志提取 `stockCode`）：

```powershell
$code = (Get-Content .\logs\app8080-latency.log -Tail 2000 | Select-String -Pattern 'market\.pubsub\.fanout\.delay stockCode=([a-z0-9]+)' | Select-Object -Last 1).Matches[0].Groups[1].Value
k6 run --vus 1 --duration 20s -e WS_URL=ws://localhost:8080/ws/market/websocket -e WS_TOKEN=<ACCESS_TOKEN> -e TARGET_CODE=$code -e WS_SESSION_MS=15000 k6/websocket-load-test.js
```

常规阶段压测：

```cmd
cd /d d:\StockSimulation\stock-simulation
k6 run --vus 200 --duration 2m -e WS_URL=ws://localhost:8080/ws/market/websocket -e WS_TOKEN=<ACCESS_TOKEN> -e TARGET_CODE=bj920000 -e WS_SESSION_MS=60000 k6/websocket-load-test.js
```

正式容量压测（按环境资源逐步拉升）：

```cmd
k6 run --vus 10000 --duration 5m -e WS_URL=ws://localhost:8080/ws/market/websocket -e WS_TOKEN=<ACCESS_TOKEN> -e TARGET_CODE=bj920000 -e WS_SESSION_MS=300000 k6/websocket-load-test.js
```

说明：

- `WS_SESSION_MS` 用于控制单次连接保持时长，避免高并发下反复握手导致错误率被放大。
- `TARGET_CODE` 必须选择当前正在推送的股票代码。若订阅了暂未推送的代码，`ws_latency_samples_total` 会为 0。
- 可从应用日志关键字 `market.pubsub.fanout.delay stockCode=` 选择活跃代码（例如 `bj920000`）。
- 单机/单实例环境下直接 `10000 VU` 可能受 CPU、网络栈与内核资源限制影响，建议先按 `200 -> 1000 -> 3000 -> 5000 -> 10000` 逐级压测。

通过标准：

- 200 VU：`ws connect success >= 95%`
- 1000 VU：`ws connect success >= 90%`
- `ws_latency_samples_total count > 0`
- `ws_push_latency_ms p(99) < 500ms`（基于 `wsPushTsMillis`）
- 服务端无持续性 5xx 峰值

备注：

- 在同机回环（k6 与服务端同机）场景，端到端延迟可能大量落在 `0~2ms`，因此出现 `p99=0` 是可能的，不代表指标无效。
- 是否“有效”以 `ws_latency_samples_total` 是否大于 0 为准。

## 7. Iteration 6 通过结论

本次交付已完成 Iteration 6 的核心后端能力与验收闭环：

- 功能层：WS 鉴权、连接管理、背压与降级、指标采集
- 质量层：新增/修正单元测试并全部通过
- 运维层：告警规则与压测脚本可直接执行
- 验收层：提供“接口可用 + 性能 + 指标”的完整可操作验收方法

满足“模块可正常工作并符合设计要求”的交付目标。
