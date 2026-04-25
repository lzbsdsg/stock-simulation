package com.lzbsdsg.stocksimulation.market.infrastructure.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lzbsdsg.stocksimulation.config.CaffeineConfig;
import com.lzbsdsg.stocksimulation.market.domain.entity.KLinePoint;
import com.lzbsdsg.stocksimulation.market.domain.entity.QuoteSnapshot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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

    verify(valueOperations)
        .set(
            eq("market:quote:sh600519"),
            eq(snapshot),
            anyLong(),
            eq(java.util.concurrent.TimeUnit.MILLISECONDS));
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
  void should_convert_legacy_quote_cache_map_to_domain_snapshot() {
    when(valueOperations.get("market:quote:sh600519"))
        .thenReturn(
            Map.ofEntries(
                Map.entry("stockCode", "sh600519"),
                Map.entry("stockName", "贵州茅台"),
                Map.entry("currentPrice", "1700.00"),
                Map.entry("openPrice", "1680.00"),
                Map.entry("closePrice", "1690.00"),
                Map.entry("highPrice", "1710.00"),
                Map.entry("lowPrice", "1675.00"),
                Map.entry("volume", "123456"),
                Map.entry("amount", "210000000.50"),
                Map.entry("changePercent", "0.59"),
                Map.entry("upperLimitPrice", "1859.00"),
                Map.entry("lowerLimitPrice", "1521.00"),
                Map.entry("timestamp", "2026-04-20T15:00:00"),
                Map.entry("source", "TENCENT")));

    MarketCacheGateway.CacheResult<QuoteSnapshot> result = marketCacheGateway.getQuote("sh600519");

    assertTrue(result.hit());
    assertEquals(MarketCacheGateway.HIT_L2, result.status());
    assertEquals("贵州茅台", result.value().getStockName());
    assertEquals(new BigDecimal("1859.00"), result.value().getUpperLimitPrice());
  }

  @Test
  void should_acquire_distributed_load_lock() {
    when(valueOperations.setIfAbsent(any(), any(), anyLong(), any())).thenReturn(Boolean.TRUE);

    boolean acquired = marketCacheGateway.tryAcquireLoadLock("sh600519");

    assertTrue(acquired);
  }

  @Test
  void should_convert_legacy_kline_cache_map_to_domain_points() {
    when(valueOperations.get("market:kline:sh600519:DAILY:2025-09-26:2026-03-25"))
        .thenReturn(
            List.of(
                Map.of(
                    "date", "2026-03-25",
                    "open", "1700.00",
                    "close", "1710.00",
                    "high", "1720.00",
                    "low", "1690.00",
                    "volume", "123456",
                    "amount", "210000000.50")));

    List<KLinePoint> result =
        marketCacheGateway.getCachedKLine("sh600519:DAILY:2025-09-26:2026-03-25");

    assertNotNull(result);
    assertEquals(1, result.size());
    assertEquals(LocalDate.parse("2026-03-25"), result.get(0).getDate());
    assertEquals(new BigDecimal("1710.00"), result.get(0).getClose());
    assertEquals(123456L, result.get(0).getVolume());
  }

  @Test
  void should_hit_kline_l1_after_cache_write() {
    List<KLinePoint> points =
        List.of(
            point(
                LocalDate.parse("2026-03-25"),
                "1700.00",
                "1710.00",
                "1720.00",
                "1690.00",
                123456L,
                "210000000.50"));

    marketCacheGateway.cacheKLine("sh600519:DAILY:2025-09-26:2026-03-25", points);
    List<KLinePoint> result =
        marketCacheGateway.getCachedKLine("sh600519:DAILY:2025-09-26:2026-03-25");

    assertNotNull(result);
    assertEquals(1, result.size());
    assertEquals(new BigDecimal("1710.00"), result.get(0).getClose());
    verify(valueOperations, never()).get("market:kline:sh600519:DAILY:2025-09-26:2026-03-25");
  }

  private KLinePoint point(
      LocalDate date,
      String open,
      String close,
      String high,
      String low,
      long volume,
      String amount) {
    KLinePoint point = new KLinePoint();
    point.setDate(date);
    point.setOpen(new BigDecimal(open));
    point.setClose(new BigDecimal(close));
    point.setHigh(new BigDecimal(high));
    point.setLow(new BigDecimal(low));
    point.setVolume(volume);
    point.setAmount(new BigDecimal(amount));
    return point;
  }
}
