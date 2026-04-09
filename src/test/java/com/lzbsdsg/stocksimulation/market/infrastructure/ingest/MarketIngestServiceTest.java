package com.lzbsdsg.stocksimulation.market.infrastructure.ingest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lzbsdsg.stocksimulation.market.domain.entity.QuoteSnapshot;
import com.lzbsdsg.stocksimulation.market.domain.entity.StockInfo;
import com.lzbsdsg.stocksimulation.market.domain.gateway.MarketDataProvider;
import com.lzbsdsg.stocksimulation.market.domain.repository.StockInfoRepository;
import com.lzbsdsg.stocksimulation.market.infrastructure.gateway.MarketCacheGateway;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class MarketIngestServiceTest {

  private MarketDataProvider provider;
  private StockInfoRepository stockInfoRepository;
  private MarketCacheGateway marketCacheGateway;
  private RedisTemplate<String, Object> redisTemplate;
  private ValueOperations<String, Object> valueOperations;
  private MarketActiveQuoteRegistry marketActiveQuoteRegistry;
  private MarketIngestService ingestService;

  @BeforeEach
  void setUp() {
    provider = org.mockito.Mockito.mock(MarketDataProvider.class);
    stockInfoRepository = org.mockito.Mockito.mock(StockInfoRepository.class);
    marketCacheGateway = org.mockito.Mockito.mock(MarketCacheGateway.class);
    redisTemplate = org.mockito.Mockito.mock(RedisTemplate.class);
    valueOperations = org.mockito.Mockito.mock(ValueOperations.class);
    marketActiveQuoteRegistry = org.mockito.Mockito.mock(MarketActiveQuoteRegistry.class);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(marketActiveQuoteRegistry.listActiveCodes(any(), anyInt())).thenReturn(List.of());

    ingestService = new MarketIngestService(
        List.of(provider),
        stockInfoRepository,
        marketCacheGateway,
        redisTemplate,
        marketActiveQuoteRegistry,
        new SimpleMeterRegistry());
  }

  @Test
  void should_ingest_and_broadcast_when_become_leader() {
    when(valueOperations.setIfAbsent(
        eq(MarketIngestService.INGEST_LEADER_KEY), any(), anyLong(), eq(TimeUnit.SECONDS)))
        .thenReturn(Boolean.TRUE);
    when(provider.isAvailable()).thenReturn(true);
    when(stockInfoRepository.findAllListed()).thenReturn(List.of(stock("sh600519")));
    QuoteSnapshot quote = quote("sh600519", "1688.88");
    when(provider.batchGetQuotes(List.of("sh600519"))).thenReturn(List.of(quote));

    ingestService.pullAndBroadcast();

    verify(marketActiveQuoteRegistry).evictStale(any(Duration.class));
    verify(marketCacheGateway).cacheQuote("sh600519", quote);
    verify(redisTemplate).convertAndSend(MarketIngestService.BROADCAST_CHANNEL, quote);
  }

  @Test
  void should_prioritize_active_codes_in_ingest_list() {
    when(marketActiveQuoteRegistry.listActiveCodes(any(), anyInt()))
        .thenReturn(List.of("sz000001", "sh600519"));
    when(stockInfoRepository.findAllListed()).thenReturn(List.of(stock("sh600519"), stock("sh601318")));

    List<String> ingestCodes = ingestService.loadIngestCodes();

    assertTrue(ingestCodes.contains("sz000001"));
    assertTrue(ingestCodes.contains("sh600519"));
  }

  @Test
  void should_renew_leader_lock_when_still_owner() {
    AtomicReference<Object> tokenHolder = new AtomicReference<>();
    when(valueOperations.setIfAbsent(
        eq(MarketIngestService.INGEST_LEADER_KEY), any(), anyLong(), eq(TimeUnit.SECONDS)))
        .thenAnswer(
            invocation -> {
              tokenHolder.set(invocation.getArgument(1));
              return Boolean.TRUE;
            });
    when(valueOperations.get(MarketIngestService.INGEST_LEADER_KEY))
        .thenAnswer(invocation -> tokenHolder.get());

    assertTrue(ingestService.ensureLeadership());

    ingestService.renewLeadership();

    verify(redisTemplate)
        .expire(
            eq(MarketIngestService.INGEST_LEADER_KEY),
            eq(MarketIngestService.LEADER_LOCK_TTL.getSeconds()),
            eq(TimeUnit.SECONDS));
  }

  @Test
  void should_failover_when_previous_leader_lost_lock() {
    when(valueOperations.setIfAbsent(
        eq(MarketIngestService.INGEST_LEADER_KEY), any(), anyLong(), eq(TimeUnit.SECONDS)))
        .thenReturn(Boolean.TRUE, Boolean.TRUE);
    when(valueOperations.get(MarketIngestService.INGEST_LEADER_KEY)).thenReturn("other-instance");

    assertTrue(ingestService.ensureLeadership());
    assertTrue(ingestService.ensureLeadership());

    verify(valueOperations, times(2))
        .setIfAbsent(eq(MarketIngestService.INGEST_LEADER_KEY), any(), anyLong(), eq(TimeUnit.SECONDS));
  }

  @Test
  void should_not_become_leader_when_lock_is_held_by_other_instance() {
    when(valueOperations.setIfAbsent(
        eq(MarketIngestService.INGEST_LEADER_KEY), any(), anyLong(), eq(TimeUnit.SECONDS)))
        .thenReturn(Boolean.FALSE);

    boolean leadership = ingestService.ensureLeadership();

    assertFalse(leadership);
  }

  private static StockInfo stock(String code) {
    StockInfo stockInfo = new StockInfo();
    stockInfo.setStockCode(code);
    stockInfo.setListed(true);
    return stockInfo;
  }

  private static QuoteSnapshot quote(String code, String price) {
    QuoteSnapshot quoteSnapshot = new QuoteSnapshot();
    quoteSnapshot.setStockCode(code);
    quoteSnapshot.setCurrentPrice(new BigDecimal(price));
    quoteSnapshot.setTimestamp(LocalDateTime.now());
    return quoteSnapshot;
  }
}
