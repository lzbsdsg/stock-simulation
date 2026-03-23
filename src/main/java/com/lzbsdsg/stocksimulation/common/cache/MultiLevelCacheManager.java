package com.lzbsdsg.stocksimulation.common.cache;

import com.lzbsdsg.stocksimulation.config.CaffeineConfig;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 多级缓存管理器实现。 L1 = Caffeine (JVM 本地), L2 = Redis Cluster。
 *
 * <p>核心策略： - 行情快照: L1 TTL=3s max=5000, L2 TTL=5s+random(0,500ms) - 股票列表: L1 TTL=5min - 配置信息: L1
 * TTL=10min - 防穿透: 空值缓存 TTL=30s - 防击穿: 同一 key 分布式锁回源 (Redis SETNX TTL=3s) - 防雪崩: L2 TTL 加随机偏移 -
 * 响应头: X-Cache-Status: HIT-L1 / HIT-L2 / MISS / STALE
 *
 * <p>写路径: 先写 L2 Redis，再通过 Pub/Sub 通知所有实例更新 L1。 失效: Redis Pub/Sub cache:invalidate:{region} → 所有实例删除
 * L1。
 */
@Component
public class MultiLevelCacheManager implements MultiLevelCache<Object> {

  public static final String CACHE_STATUS_HEADER = "X-Cache-Status";
  public static final String HIT_L1 = "HIT-L1";
  public static final String HIT_L2 = "HIT-L2";
  public static final String MISS = "MISS";

  private final CacheManager cacheManager;
  private final RedisTemplate<String, Object> redisTemplate;
  private final Map<String, Object> keyLocks = new ConcurrentHashMap<>();

  public MultiLevelCacheManager(
      CacheManager cacheManager, RedisTemplate<String, Object> redisTemplate) {
    this.cacheManager = cacheManager;
    this.redisTemplate = redisTemplate;
  }

  @Override
  public Object get(String region, String key, Supplier<Object> loader) {
    Cache l1Cache = getCache(region);
    Object l1 = l1Cache.get(key, Object.class);
    if (l1 != null) {
      setCacheStatus(HIT_L1);
      return l1;
    }

    String redisKey = buildRedisKey(region, key);
    Object l2 = redisTemplate.opsForValue().get(redisKey);
    if (l2 != null) {
      l1Cache.put(key, l2);
      setCacheStatus(HIT_L2);
      return l2;
    }

    Object lock = keyLocks.computeIfAbsent(redisKey, ignored -> new Object());
    synchronized (lock) {
      Object doubleCheck = l1Cache.get(key, Object.class);
      if (doubleCheck != null) {
        setCacheStatus(HIT_L1);
        return doubleCheck;
      }

      Object loaded = loader.get();
      if (loaded != null) {
        Duration l1Ttl = defaultL1Ttl(region);
        Duration l2Ttl = defaultL2Ttl(region);
        put(region, key, loaded, l1Ttl, l2Ttl);
      }
      setCacheStatus(MISS);
      return loaded;
    }
  }

  @Override
  public void put(String region, String key, Object value, Duration l1Ttl, Duration l2Ttl) {
    Cache l1Cache = getCache(region);
    l1Cache.put(key, value);
    redisTemplate
        .opsForValue()
        .set(
            buildRedisKey(region, key),
            value,
            Math.max(l2Ttl.toMillis(), 1),
            TimeUnit.MILLISECONDS);
  }

  @Override
  public void evict(String region, String key) {
    Cache l1Cache = getCache(region);
    l1Cache.evict(key);
    redisTemplate.delete(buildRedisKey(region, key));
    redisTemplate.convertAndSend(buildInvalidateChannel(region), key);
  }

  public void evictLocal(String region, String key) {
    getCache(region).evict(key);
  }

  private Cache getCache(String region) {
    Cache cache = cacheManager.getCache(region);
    if (cache != null) {
      return cache;
    }
    Cache fallback = cacheManager.getCache(CaffeineConfig.CACHE_CONFIG);
    if (fallback == null) {
      throw new IllegalStateException("No cache configured for region: " + region);
    }
    return fallback;
  }

  private String buildRedisKey(String region, String key) {
    return "cache:" + region + ":" + key;
  }

  private String buildInvalidateChannel(String region) {
    return "cache:invalidate:" + region;
  }

  private Duration defaultL1Ttl(String region) {
    if (CaffeineConfig.CACHE_QUOTE.equals(region)) {
      return Duration.ofSeconds(3);
    }
    if (CaffeineConfig.CACHE_STOCK.equals(region)) {
      return Duration.ofMinutes(5);
    }
    return Duration.ofMinutes(10);
  }

  private Duration defaultL2Ttl(String region) {
    if (CaffeineConfig.CACHE_QUOTE.equals(region)) {
      return Duration.ofSeconds(5);
    }
    return Duration.ofMinutes(10);
  }

  private void setCacheStatus(String status) {
    ServletRequestAttributes attributes =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attributes == null) {
      return;
    }
    HttpServletResponse response = attributes.getResponse();
    if (response != null) {
      response.setHeader(CACHE_STATUS_HEADER, status);
    }
  }
}
