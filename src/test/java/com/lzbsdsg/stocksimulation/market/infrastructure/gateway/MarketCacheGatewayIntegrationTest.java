package com.lzbsdsg.stocksimulation.market.infrastructure.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lzbsdsg.stocksimulation.config.CaffeineConfig;
import com.lzbsdsg.stocksimulation.market.domain.entity.QuoteSnapshot;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class MarketCacheGatewayIntegrationTest {

  private RedisTemplate<String, Object> redisTemplate;
  private ValueOperations<String, Object> valueOperations;
  private MarketCacheGateway marketCacheGateway;

  @BeforeEach
  void setUp() {
    redisTemplate = org.mockito.Mockito.mock(RedisTemplate.class);
    valueOperations = org.mockito.Mockito.mock(ValueOperations.class);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);

    CacheManager cacheManager = new CaffeineConfig().cacheManager();
    marketCacheGateway = new MarketCacheGateway(cacheManager, redisTemplate);
  }

  @Test
  void should_cache_quote_into_l1_and_l2() {
    QuoteSnapshot snapshot = new QuoteSnapshot();
    snapshot.setStockCode("sh600519");
    snapshot.setCurrentPrice(new BigDecimal("1688.88"));
    snapshot.setTimestamp(LocalDateTime.now());

    marketCacheGateway.cacheQuote("sh600519", snapshot);

    verify(valueOperations).set(eq("market:quote:sh600519"), eq(snapshot), anyLong(), eq(java.util.concurrent.TimeUnit.MILLISECONDS));
    verify(valueOperations)
        .set(
            eq("market:quote:stale:sh600519"),
            eq(snapshot),
            eq(300L),
            eq(java.util.concurrent.TimeUnit.SECONDS));
  }

  @Test
  void should_hit_l2_and_backfill_l1() {
    QuoteSnapshot snapshot = new QuoteSnapshot();
    snapshot.setStockCode("sh600519");
    snapshot.setCurrentPrice(new BigDecimal("1700.00"));
    snapshot.setTimestamp(LocalDateTime.now());
    when(valueOperations.get("market:quote:sh600519")).thenReturn(snapshot);

    MarketCacheGateway.CacheResult<QuoteSnapshot> result = marketCacheGateway.getQuote("sh600519");

    assertTrue(result.hit());
    assertEquals(MarketCacheGateway.HIT_L2, result.status());
    assertEquals("1700.00", result.value().getCurrentPrice().toPlainString());
  }

  @Test
  void should_acquire_distributed_load_lock() {
    when(valueOperations.setIfAbsent(any(), any(), anyLong(), any())).thenReturn(Boolean.TRUE);

    boolean acquired = marketCacheGateway.tryAcquireLoadLock("sh600519");

    assertTrue(acquired);
  }
}
