package com.lzbsdsg.stocksimulation.market.infrastructure.ingest;

/**
 * 行情 Pub/Sub 订阅监听器。 所有 App 实例订阅 Redis channel: market:quote:broadcast
 *
 * <p>收到广播消息后： 1. 反序列化行情数据 2. 更新本地 L1 Caffeine 缓存 3. 触发 MarketWebSocketHandler 推送到该实例管理的 WS 连接
 *
 * <p>注意： - 序列化异常应捕获并忽略（日志记录），不影响其他消息处理 - 更新 L1 缓存时使用 putIfAbsent 语义，避免覆盖更新的数据
 */
public class MarketPubSubListener {
  // TODO: implements MessageListener
  // TODO: 注入 CaffeineCacheManager, MarketWebSocketHandler
  // TODO: onMessage() → 更新 L1 → 推送 WS
}
