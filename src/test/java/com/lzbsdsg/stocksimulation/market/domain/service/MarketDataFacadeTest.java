package com.lzbsdsg.stocksimulation.market.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lzbsdsg.stocksimulation.common.exception.BizException;
import com.lzbsdsg.stocksimulation.market.domain.entity.KLinePeriod;
import com.lzbsdsg.stocksimulation.market.domain.entity.KLinePoint;
import com.lzbsdsg.stocksimulation.market.domain.entity.QuoteSnapshot;
import com.lzbsdsg.stocksimulation.market.domain.gateway.MarketDataProvider;
import com.lzbsdsg.stocksimulation.market.infrastructure.gateway.MarketCacheGateway;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 行情数据门面单元测试。 覆盖：多级缓存命中路径、Provider降级、缓存穿透防护。 */
@ExtendWith(MockitoExtension.class)
class MarketDataFacadeTest {

  @Mock private MarketDataProvider primaryProvider;
  @Mock private MarketDataProvider backupProvider;
  @Mock private MarketCacheGateway marketCacheGateway;
  @Mock private HistoricalKLineService historicalKLineService;

  private MarketDataFacade marketDataFacade;

  @BeforeEach
  void setUp() {
    marketDataFacade =
        new MarketDataFacade(
            List.of(primaryProvider, backupProvider), marketCacheGateway, historicalKLineService);
  }

  @Test
  void should_return_from_l1_cache_hit() {
    QuoteSnapshot cached = quote("sh600519", "贵州茅台", "1688.88");
    when(marketCacheGateway.getQuote("sh600519"))
        .thenReturn(MarketCacheGateway.CacheResult.hit(cached, MarketCacheGateway.HIT_L1));

    QuoteSnapshot result = marketDataFacade.getQuote("sh600519");

    assertEquals("sh600519", result.getStockCode());
    verify(primaryProvider, never()).isAvailable();
  }

  @Test
  void should_call_provider_on_cache_miss() {
    QuoteSnapshot fromProvider = quote("sh600519", "贵州茅台", "1700.01");
    when(marketCacheGateway.getQuote("sh600519")).thenReturn(MarketCacheGateway.CacheResult.miss());
    when(marketCacheGateway.getStaleQuote("sh600519")).thenReturn(null);
    when(marketCacheGateway.tryAcquireLoadLock("sh600519")).thenReturn(true);
    when(primaryProvider.getQuote("sh600519")).thenReturn(fromProvider);

    QuoteSnapshot result = marketDataFacade.getQuote("sh600519");

    assertEquals(new BigDecimal("1700.01"), result.getCurrentPrice());
    verify(marketCacheGateway).cacheQuote("sh600519", fromProvider);
    verify(marketCacheGateway).releaseLoadLock("sh600519");
  }

  @Test
  void should_fallback_to_backup_provider_on_primary_failure() {
    QuoteSnapshot fromBackup = quote("sz000001", "平安银行", "12.34");
    when(marketCacheGateway.getQuote("sz000001")).thenReturn(MarketCacheGateway.CacheResult.miss());
    when(marketCacheGateway.getStaleQuote("sz000001")).thenReturn(null);
    when(marketCacheGateway.tryAcquireLoadLock("sz000001")).thenReturn(true);
    when(primaryProvider.getQuote("sz000001")).thenThrow(new RuntimeException("timeout"));
    when(backupProvider.getQuote("sz000001")).thenReturn(fromBackup);

    QuoteSnapshot result = marketDataFacade.getQuote("sz000001");

    assertEquals("平安银行", result.getStockName());
    verify(marketCacheGateway).cacheQuote("sz000001", fromBackup);
  }

  @Test
  void should_use_stale_when_all_providers_failed() {
    QuoteSnapshot stale = quote("sh600519", "贵州茅台", "1688.00");
    when(marketCacheGateway.getQuote("sh600519")).thenReturn(MarketCacheGateway.CacheResult.miss());
    when(marketCacheGateway.getStaleQuote("sh600519")).thenReturn(stale);
    when(marketCacheGateway.tryAcquireLoadLock("sh600519")).thenReturn(true);
    when(primaryProvider.getQuote("sh600519")).thenThrow(new RuntimeException("error"));

    QuoteSnapshot result = marketDataFacade.getQuote("sh600519");

    assertNotNull(result);
    assertEquals(new BigDecimal("1688.00"), result.getCurrentPrice());
  }

  @Test
  void should_cache_null_value_to_prevent_penetration_when_all_failed() {
    when(marketCacheGateway.getQuote("sh999999")).thenReturn(MarketCacheGateway.CacheResult.miss());
    when(marketCacheGateway.getStaleQuote("sh999999")).thenReturn(null);
    when(marketCacheGateway.tryAcquireLoadLock("sh999999")).thenReturn(true);
    when(primaryProvider.getQuote("sh999999")).thenThrow(new RuntimeException("unavailable"));
    when(backupProvider.getQuote("sh999999")).thenThrow(new RuntimeException("unavailable"));

    assertThrows(BizException.class, () -> marketDataFacade.getQuote("sh999999"));

    verify(marketCacheGateway).cacheNullQuote("sh999999");
  }

  @Test
  void should_get_kline_from_historical_service() {
    String klineCacheKey = "sh600519:DAILY:2026-03-01:2026-03-02";
    when(marketCacheGateway.getCachedKLine(klineCacheKey)).thenReturn(null);
    when(marketCacheGateway.tryAcquireKLineLoadLock("kline:" + klineCacheKey)).thenReturn(true);

    KLinePoint p = new KLinePoint();
    p.setDate(LocalDate.now());
    p.setClose(new BigDecimal("10.00"));
    when(historicalKLineService.getKLine(
            "sh600519",
            KLinePeriod.DAILY,
            LocalDate.parse("2026-03-01"),
            LocalDate.parse("2026-03-02")))
        .thenReturn(List.of(p));

    List<KLinePoint> result =
        marketDataFacade.getKLine(
            "sh600519",
            KLinePeriod.DAILY,
            LocalDate.parse("2026-03-01"),
            LocalDate.parse("2026-03-02"));

    assertEquals(1, result.size());
    verify(marketCacheGateway).releaseLoadLock("kline:" + klineCacheKey);
    verify(primaryProvider, never())
        .getKLine(
            "sh600519",
            KLinePeriod.DAILY,
            LocalDate.parse("2026-03-01"),
            LocalDate.parse("2026-03-02"));
  }

  @Test
  void should_use_stale_immediately_when_waiting_for_quote_lock_and_stale_exists() {
    QuoteSnapshot stale = quote("sh600519", "贵州茅台", "1686.66");
    when(marketCacheGateway.getQuote("sh600519")).thenReturn(MarketCacheGateway.CacheResult.miss());
    when(marketCacheGateway.getStaleQuote("sh600519")).thenReturn(stale);
    when(marketCacheGateway.tryAcquireLoadLock("sh600519")).thenReturn(false);

    QuoteSnapshot result = marketDataFacade.getQuote("sh600519");

    assertEquals(new BigDecimal("1686.66"), result.getCurrentPrice());
    verify(primaryProvider, never()).getQuote("sh600519");
  }

  @Test
  void should_batch_get_quotes_with_partial_cache_hit() {
    QuoteSnapshot hit = quote("sh600519", "贵州茅台", "1688.88");
    QuoteSnapshot loaded = quote("sz000001", "平安银行", "12.34");
    when(marketCacheGateway.getQuote("sh600519"))
        .thenReturn(MarketCacheGateway.CacheResult.hit(hit, MarketCacheGateway.HIT_L1));
    when(marketCacheGateway.getQuote("sz000001")).thenReturn(MarketCacheGateway.CacheResult.miss());
    when(marketCacheGateway.getStaleQuote("sz000001")).thenReturn(null);
    when(primaryProvider.batchGetQuotes(List.of("sz000001"))).thenReturn(List.of(loaded));

    List<QuoteSnapshot> result = marketDataFacade.batchGetQuotes(List.of("sh600519", "sz000001"));

    assertEquals(2, result.size());
    assertEquals("sh600519", result.get(0).getStockCode());
    assertEquals("sz000001", result.get(1).getStockCode());
    verify(primaryProvider).batchGetQuotes(List.of("sz000001"));
    verify(marketCacheGateway).cacheQuote("sz000001", loaded);
  }

  @Test
  void should_batch_get_quotes_without_provider_when_all_cache_hit() {
    QuoteSnapshot quote1 = quote("sh600519", "贵州茅台", "1688.88");
    QuoteSnapshot quote2 = quote("sz000001", "平安银行", "12.34");
    when(marketCacheGateway.getQuote("sh600519"))
        .thenReturn(MarketCacheGateway.CacheResult.hit(quote1, MarketCacheGateway.HIT_L1));
    when(marketCacheGateway.getQuote("sz000001"))
        .thenReturn(MarketCacheGateway.CacheResult.hit(quote2, MarketCacheGateway.HIT_L2));

    List<QuoteSnapshot> result = marketDataFacade.batchGetQuotes(List.of("sh600519", "sz000001"));

    assertEquals(2, result.size());
    verify(primaryProvider, never()).batchGetQuotes(List.of("sh600519", "sz000001"));
  }

  @Test
  void should_batch_get_quotes_with_plain_six_digit_codes() {
    QuoteSnapshot loaded1 = quote("sz000001", "平安银行", "12.34");
    QuoteSnapshot loaded2 = quote("sz000004", "国华网安", "8.76");

    when(marketCacheGateway.getQuote("sz000001")).thenReturn(MarketCacheGateway.CacheResult.miss());
    when(marketCacheGateway.getQuote("sz000004")).thenReturn(MarketCacheGateway.CacheResult.miss());
    when(marketCacheGateway.getStaleQuote("sz000001")).thenReturn(null);
    when(marketCacheGateway.getStaleQuote("sz000004")).thenReturn(null);
    when(primaryProvider.batchGetQuotes(List.of("sz000001", "sz000004")))
        .thenReturn(List.of(loaded1, loaded2));

    List<QuoteSnapshot> result = marketDataFacade.batchGetQuotes(List.of("000001", "000004"));

    assertEquals(2, result.size());
    assertEquals("sz000001", result.get(0).getStockCode());
    assertEquals("sz000004", result.get(1).getStockCode());
    verify(primaryProvider).batchGetQuotes(List.of("sz000001", "sz000004"));
    verify(marketCacheGateway).cacheQuote("sz000001", loaded1);
    verify(marketCacheGateway).cacheQuote("sz000004", loaded2);
  }

  @Test
  void should_batch_get_quotes_use_stale_for_provider_miss() {
    QuoteSnapshot stale = quote("sz000001", "平安银行", "12.30");
    when(marketCacheGateway.getQuote("sz000001")).thenReturn(MarketCacheGateway.CacheResult.miss());
    when(marketCacheGateway.getStaleQuote("sz000001")).thenReturn(stale);
    when(primaryProvider.batchGetQuotes(List.of("sz000001"))).thenReturn(List.of());
    when(backupProvider.batchGetQuotes(List.of("sz000001"))).thenReturn(List.of());

    List<QuoteSnapshot> result = marketDataFacade.batchGetQuotes(List.of("sz000001"));

    assertEquals(1, result.size());
    assertEquals(new BigDecimal("12.30"), result.get(0).getCurrentPrice());
  }

  private QuoteSnapshot quote(String code, String name, String price) {
    QuoteSnapshot snapshot = new QuoteSnapshot();
    snapshot.setStockCode(code);
    snapshot.setStockName(name);
    snapshot.setCurrentPrice(new BigDecimal(price));
    snapshot.setClosePrice(new BigDecimal(price));
    snapshot.setTimestamp(LocalDateTime.now());
    return snapshot;
  }
}
