package com.lzbsdsg.stocksimulation.common.cache;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 多级缓存抽象接口。 读路径：L1 Caffeine → L2 Redis → Supplier 回源；L2 命中时回填 L1。 写路径：先写 L2，再通过 Redis Pub/Sub
 * 通知所有实例更新 L1。
 *
 * @param <T> 缓存值类型
 */
public interface MultiLevelCache<T> {

  /** 读取缓存，未命中则调用 loader 回源并写入两级缓存。 */
  T get(String region, String key, Supplier<T> loader);

  /** 写入两级缓存。 */
  void put(String region, String key, T value, Duration l1Ttl, Duration l2Ttl);

  /** 删除两级缓存（通过 Pub/Sub 通知所有实例）。 */
  void evict(String region, String key);
}
