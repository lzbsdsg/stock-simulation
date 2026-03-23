package com.lzbsdsg.stocksimulation.common.cache;

/**
 * Redis Pub/Sub 缓存失效监听器。 订阅 channel: cache:invalidate:{region} 收到消息后删除本实例的 Caffeine L1 缓存对应条目。
 *
 * <p>用于多实例部署时保证 L1 缓存一致性： 任意实例写入/删除缓存 → 发布 Pub/Sub → 所有实例清除本地 L1。
 */
public class CacheInvalidateListener {
  // TODO: implements MessageListener
  // TODO: 注入 CaffeineCacheManager，接收消息后 evict 对应 region + key
}
