# Skill Card: 消息推送（Notification / Push）

## 触发句式

> 请帮我生成消息推送模块（notification），包含 WebSocket 实时行情推送、交易成交通知、系统公告。

---

## 输入规范

| 信息 | 默认值 |
|---|---|
| 推送协议 | WebSocket (STOMP over SockJS) |
| 行情推送频率 | 3s 一次（自选股） |
| 成交通知 | 异步发送（MQ消费 → WS推送 + 站内信） |
| 断线重连 | 自动重连，指数退避 1s/2s/4s/8s/16s max |
| 连接上限 | 单用户 3 个WS连接 |

---

## 输出要求（必须依次输出）

1. **Assumptions** — MQ与WS的职责边界、推送失败策略
2. **目录树** — `com.lzbsdsg.stocksimulation.notification`
3. **核心类职责**
   - `WebSocketConfig` — STOMP配置
   - `MarketPushScheduler` — 定时推送自选股行情
   - `TradePushConsumer` — MQ消费成交事件 → WS推送
   - `NotificationController` — 站内信列表接口
   - `NotificationDomainService` — 消息生成与去重
4. **API 契约** — WS /ws/market (SUBSCRIBE /topic/quotes/{userId}), GET /notifications, PUT /notifications/{id}/read
5. **Flyway SQL** — `t_notification_message` 站内信表
6. **事务与一致性** — MQ消费幂等（messageId去重）、WS推送失败落库
7. **测试策略** — WS连接/断线/重连、MQ消费幂等、推送延迟测试

---

## 质量验收标准

- [ ] WS断线自动重连（前端+后端心跳）
- [ ] 推送消息不丢（MQ持久化 + ACK）
- [ ] 连接数限制生效
- [ ] 站内信已读/未读状态正确

---

## 常见陷阱

| 陷阱 | 规避 |
|---|---|
| WS内存泄漏 | 连接生命周期管理 + 心跳超时断开 |
| 推送风暴 | 行情推送频率限制 + 只推送自选股 |
| MQ消息重复 | 消费端幂等（messageId去重表） |
| 前端重复订阅 | 订阅管理 + unsubscribe on unmount |
