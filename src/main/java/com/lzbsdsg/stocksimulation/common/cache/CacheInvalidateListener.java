package com.lzbsdsg.stocksimulation.common.cache;

import java.nio.charset.StandardCharsets;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * Redis Pub/Sub 缓存失效监听器。 订阅 channel: cache:invalidate:{region} 收到消息后删除本实例的 Caffeine L1 缓存对应条目。
 *
 * <p>用于多实例部署时保证 L1 缓存一致性： 任意实例写入/删除缓存 → 发布 Pub/Sub → 所有实例清除本地 L1。
 */
@Component
public class CacheInvalidateListener implements MessageListener {
  private final MultiLevelCacheManager cacheManager;

  public CacheInvalidateListener(MultiLevelCacheManager cacheManager) {
    this.cacheManager = cacheManager;
  }

  @Override
  public void onMessage(Message message, byte[] pattern) {
    String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
    String key = new String(message.getBody(), StandardCharsets.UTF_8);
    String prefix = "cache:invalidate:";
    if (!channel.startsWith(prefix) || key.isBlank()) {
      return;
    }
    String region = channel.substring(prefix.length());
    cacheManager.evictLocal(region, key);
  }
}
