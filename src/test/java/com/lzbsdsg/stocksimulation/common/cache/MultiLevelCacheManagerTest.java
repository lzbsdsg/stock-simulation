package com.lzbsdsg.stocksimulation.common.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lzbsdsg.stocksimulation.config.CaffeineConfig;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class MultiLevelCacheManagerTest {

  private RedisTemplate<String, Object> redisTemplate;
  private ValueOperations<String, Object> valueOperations;
  private MultiLevelCacheManager cacheManager;
  private CacheManager l1CacheManager;

  @BeforeEach
  void setUp() {
    CaffeineConfig caffeineConfig = new CaffeineConfig();
    l1CacheManager = caffeineConfig.cacheManager();

    redisTemplate = mock(RedisTemplate.class);
    valueOperations = mock(ValueOperations.class);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);

    cacheManager = new MultiLevelCacheManager(l1CacheManager, redisTemplate);
  }

  @Test
  void should_hit_l1_when_value_exists_in_caffeine() {
    l1CacheManager.getCache(CaffeineConfig.CACHE_QUOTE).put("sh600519", "l1-value");

    Object value =
        cacheManager.get(
            CaffeineConfig.CACHE_QUOTE,
            "sh600519",
            () -> {
              throw new IllegalStateException("loader should not run");
            });

    assertEquals("l1-value", value);
    verify(redisTemplate, never()).opsForValue();
  }

  @Test
  void should_hit_l2_and_backfill_l1_when_l1_miss() {
    when(valueOperations.get("cache:quote:sh600519")).thenReturn("l2-value");

    Object first = cacheManager.get(CaffeineConfig.CACHE_QUOTE, "sh600519", () -> "from-loader");
    when(valueOperations.get("cache:quote:sh600519")).thenReturn(null);
    Object second = cacheManager.get(CaffeineConfig.CACHE_QUOTE, "sh600519", () -> "from-loader");

    assertEquals("l2-value", first);
    assertEquals("l2-value", second);
  }

  @Test
  void should_load_and_write_l1_l2_when_miss() {
    when(valueOperations.get("cache:quote:sh600000")).thenReturn(null);
    AtomicInteger count = new AtomicInteger();

    Object value =
        cacheManager.get(
            CaffeineConfig.CACHE_QUOTE,
            "sh600000",
            () -> {
              count.incrementAndGet();
              return "loader-value";
            });

    assertEquals("loader-value", value);
    assertEquals(1, count.get());
    verify(valueOperations)
        .set(eq("cache:quote:sh600000"), eq("loader-value"), anyLong(), eq(TimeUnit.MILLISECONDS));
  }

  @Test
  void should_invalidate_local_l1_when_pubsub_message_received() {
    cacheManager.put(
        CaffeineConfig.CACHE_QUOTE,
        "sh601318",
        "value",
        Duration.ofSeconds(3),
        Duration.ofSeconds(5));

    CacheInvalidateListener listener = new CacheInvalidateListener(cacheManager);
    Message message =
        new DefaultMessage(
            "cache:invalidate:quote".getBytes(StandardCharsets.UTF_8),
            "sh601318".getBytes(StandardCharsets.UTF_8));
    listener.onMessage(message, null);

    Object localValue =
        l1CacheManager.getCache(CaffeineConfig.CACHE_QUOTE).get("sh601318", Object.class);
    assertNull(localValue);
  }

  @Test
  void should_expire_l1_by_ttl() throws InterruptedException {
    l1CacheManager.getCache(CaffeineConfig.CACHE_QUOTE).put("sh600519", "ttl-value");

    Thread.sleep(3200);

    Object localValue =
        l1CacheManager.getCache(CaffeineConfig.CACHE_QUOTE).get("sh600519", Object.class);
    assertNull(localValue);
  }
}
