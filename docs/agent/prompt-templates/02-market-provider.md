# Skill Card: 行情接入（Market Provider）

## 触发句式

> 请帮我生成行情模块（market），包含 Provider 抽象层、Sina/Tencent/Mock 适配器、Redis 缓存、降级机制、WebSocket 推送。

---

## 输入规范

| 信息 | 默认值 |
|---|---|
| 主数据源 | 新浪财经 HTTP API |
| 备用数据源 | 腾讯财经 HTTP API |
| 快照缓存 TTL | 5s |
| K线缓存 TTL | 60s |
| 节流窗口 | 同一股票 3s 内去重 |
| 批量上限 | 一次最多 50 只 |
| 降级策略 | 主源失败 → 备源 → 最近缓存 → 错误码 |
| 推送方式 | WebSocket (STOMP over SockJS) |

---

## 输出要求（必须依次输出）

1. **Assumptions** — 第三方API可用性、Mock数据范围
2. **目录树** — `com.lzbsdsg.stocksimulation.market` 下的包与文件
3. **核心类职责**
   - `MarketDataProvider` (interface) — 行情抽象接口
   - `SinaMarketDataAdapter` — 新浪实现
   - `TencentMarketDataAdapter` — 腾讯实现
   - `MockMarketDataAdapter` — 本地Mock
   - `MarketDataFacade` — 缓存 + 降级 + 节流编排
   - `MarketCacheGateway` — Redis缓存读写
   - `MarketWebSocketHandler` — WS推送
   - `QuoteSnapshot` / `KLinePoint` — 领域值对象
4. **API 契约** — GET /market/quote/{code}, GET /market/quotes?codes=, GET /market/kline/{code}, WS /ws/market
5. **Flyway SQL** — `t_market_stock_info`（股票基础信息表）
6. **事务与一致性** — 缓存一致性（TTL兜底）、降级状态机
7. **测试策略** — Mock Provider返回、缓存命中/穿透、降级切换、WS推送

---

## 质量验收标准

- [ ] Provider 接口与实现解耦（可通过配置切换）
- [ ] 缓存穿透防护（空值缓存 / Bloom Filter）
- [ ] 降级链路正确：主源 → 备源 → 缓存 → 错误码
- [ ] 节流生效：3s内重复请求不穿透
- [ ] Mock模式可独立运行，不依赖外部网络

---

## 常见陷阱

| 陷阱 | 规避 |
|---|---|
| 第三方限流/封IP | 请求节流 + 缓存 + 多源轮换 |
| 缓存雪崩 | TTL加随机抖动(±500ms) |
| 缓存穿透 | 对不存在的股票代码缓存空值30s |
| WS连接泄漏 | 心跳检测 + 超时断开 + 连接数上限 |
| 数据格式变化 | Adapter 内做防御性解析，异常不扩散 |
