package com.lzbsdsg.stocksimulation.market.domain.service;

import com.lzbsdsg.stocksimulation.common.exception.BizException;
import com.lzbsdsg.stocksimulation.common.result.ErrorCode;
import com.lzbsdsg.stocksimulation.market.domain.entity.KLinePeriod;
import com.lzbsdsg.stocksimulation.market.domain.entity.KLinePoint;
import com.lzbsdsg.stocksimulation.market.domain.entity.QuoteSnapshot;
import com.lzbsdsg.stocksimulation.market.domain.gateway.MarketDataProvider;
import com.lzbsdsg.stocksimulation.market.infrastructure.gateway.MarketCacheGateway;
import com.lzbsdsg.stocksimulation.market.infrastructure.resilience.ProviderCircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDate;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 行情数据门面（领域服务）
 *
 * <p>负责协调 Provider + 缓存 + 降级策略
 */
@Slf4j
@Service
public class MarketDataFacade {

  private final List<MarketDataProvider> providers;
  private final MarketCacheGateway marketCacheGateway;
  private final Counter providerFallbackCounter;
  private final Map<String, ProviderCircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();

  @Autowired
  public MarketDataFacade(
      List<MarketDataProvider> providers,
      MarketCacheGateway marketCacheGateway,
      MeterRegistry meterRegistry) {
    this.providers = providers;
    this.marketCacheGateway = marketCacheGateway;
    this.providerFallbackCounter = meterRegistry.counter("market.provider.fallback.total");
  }

  MarketDataFacade(List<MarketDataProvider> providers, MarketCacheGateway marketCacheGateway) {
    this(providers, marketCacheGateway, new SimpleMeterRegistry());
  }

  /** 获取单只股票行情（先缓存 → Provider → 降级） */
  public QuoteSnapshot getQuote(String stockCode) {
    String normalizedCode = normalizeStockCode(stockCode);
    MarketCacheGateway.CacheResult<QuoteSnapshot> cacheResult = marketCacheGateway.getQuote(normalizedCode);
    if (cacheResult.hit()) {
      marketCacheGateway.setCacheStatusHeader(cacheResult.status());
      if (cacheResult.nullValue()) {
        throw new BizException(ErrorCode.MARKET_STOCK_NOT_FOUND);
      }
      return cacheResult.value();
    }

    boolean acquiredLock = marketCacheGateway.tryAcquireLoadLock(normalizedCode);
    if (!acquiredLock) {
      sleepSilently(80);
      MarketCacheGateway.CacheResult<QuoteSnapshot> lockWaitResult = marketCacheGateway.getQuote(normalizedCode);
      if (lockWaitResult.hit()) {
        marketCacheGateway.setCacheStatusHeader(lockWaitResult.status());
        if (lockWaitResult.nullValue()) {
          throw new BizException(ErrorCode.MARKET_STOCK_NOT_FOUND);
        }
        return lockWaitResult.value();
      }
    }

    try {
      QuoteSnapshot loaded = loadQuoteFromProviders(normalizedCode);
      if (loaded != null) {
        marketCacheGateway.cacheQuote(normalizedCode, loaded);
        marketCacheGateway.setCacheStatusHeader(MarketCacheGateway.MISS);
        return loaded;
      }
    } finally {
      if (acquiredLock) {
        marketCacheGateway.releaseLoadLock(normalizedCode);
      }
    }

    QuoteSnapshot staleQuote = marketCacheGateway.getStaleQuote(normalizedCode);
    if (staleQuote != null) {
      marketCacheGateway.setCacheStatusHeader(MarketCacheGateway.STALE);
      return staleQuote;
    }

    marketCacheGateway.cacheNullQuote(normalizedCode);
    marketCacheGateway.setCacheStatusHeader(MarketCacheGateway.MISS);
    throw new BizException(ErrorCode.MARKET_STOCK_NOT_FOUND);
  }

  /** 批量获取行情 */
  public List<QuoteSnapshot> batchGetQuotes(List<String> stockCodes) {
    List<String> normalizedCodes =
        stockCodes.stream().map(this::normalizeStockCode).filter(code -> !code.isBlank()).distinct().toList();
    if (normalizedCodes.isEmpty()) {
      return List.of();
    }

    Map<String, QuoteSnapshot> resolvedQuotes = new LinkedHashMap<>();
    List<String> misses = new ArrayList<>();
    boolean hasL2Hit = false;

    for (String code : normalizedCodes) {
      MarketCacheGateway.CacheResult<QuoteSnapshot> cacheResult = marketCacheGateway.getQuote(code);
      if (cacheResult.hit()) {
        if (cacheResult.nullValue()) {
          continue;
        }
        resolvedQuotes.put(code, cacheResult.value());
        if (MarketCacheGateway.HIT_L2.equals(cacheResult.status())) {
          hasL2Hit = true;
        }
      } else {
        misses.add(code);
      }
    }

    boolean hasStale = false;
    if (!misses.isEmpty()) {
      Map<String, QuoteSnapshot> providerResult = loadBatchFromProviders(misses);
      for (String missCode : misses) {
        QuoteSnapshot loaded = providerResult.get(missCode);
        if (loaded != null) {
          marketCacheGateway.cacheQuote(missCode, loaded);
          resolvedQuotes.put(missCode, loaded);
          continue;
        }

        QuoteSnapshot stale = marketCacheGateway.getStaleQuote(missCode);
        if (stale != null) {
          resolvedQuotes.put(missCode, stale);
          hasStale = true;
          continue;
        }

        marketCacheGateway.cacheNullQuote(missCode);
      }
    }

    if (resolvedQuotes.isEmpty()) {
      throw new BizException(ErrorCode.MARKET_DATA_UNAVAILABLE);
    }

    if (misses.isEmpty()) {
      marketCacheGateway.setCacheStatusHeader(hasL2Hit ? MarketCacheGateway.HIT_L2 : MarketCacheGateway.HIT_L1);
    } else if (hasStale) {
      marketCacheGateway.setCacheStatusHeader(MarketCacheGateway.STALE);
    } else {
      marketCacheGateway.setCacheStatusHeader(MarketCacheGateway.MISS);
    }

    List<QuoteSnapshot> quotes = new ArrayList<>();
    for (String stockCode : normalizedCodes) {
      QuoteSnapshot quote = resolvedQuotes.get(stockCode);
      if (quote != null) {
        quotes.add(quote);
      }
    }
    return quotes;
  }

  /** 获取K线 */
  public List<KLinePoint> getKLine(
      String stockCode, KLinePeriod period, LocalDate from, LocalDate to) {
    String key = buildKLineCacheKey(stockCode, period, from, to);
    List<KLinePoint> cached = marketCacheGateway.getCachedKLine(key);
    if (cached != null) {
      marketCacheGateway.setCacheStatusHeader(MarketCacheGateway.HIT_L2);
      return cached;
    }

    for (MarketDataProvider provider : providers) {
      if (provider.isAvailable()) {
        try {
          List<KLinePoint> points = provider.getKLine(stockCode, period, from, to);
          marketCacheGateway.cacheKLine(key, points);
          marketCacheGateway.setCacheStatusHeader(MarketCacheGateway.MISS);
          return points;
        } catch (Exception e) {
          log.warn(
              "Provider {} getKLine failed for {}: {}",
              provider.getClass().getSimpleName(),
              stockCode,
              e.getMessage());
        }
      }
    }
    throw new BizException(ErrorCode.MARKET_DATA_UNAVAILABLE);
  }

  private String normalizeStockCode(String stockCode) {
    if (stockCode == null) {
      return "";
    }

    String code = stockCode.trim().toLowerCase(Locale.ROOT);
    if (code.startsWith("sh") || code.startsWith("sz")) {
      return code;
    }
    if (code.matches("^6\\d{5}$")) {
      return "sh" + code;
    }
    if (code.matches("^[03]\\d{5}$")) {
      return "sz" + code;
    }
    return code;
  }

  private String buildKLineCacheKey(
      String stockCode, KLinePeriod period, LocalDate from, LocalDate to) {
    DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;
    return normalizeStockCode(stockCode)
        + ":"
        + period.name()
        + ":"
        + from.format(formatter)
        + ":"
        + to.format(formatter);
  }

  private void sleepSilently(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private QuoteSnapshot loadQuoteFromProviders(String stockCode) {
    for (MarketDataProvider provider : providers) {
      ProviderCircuitBreaker breaker = circuitBreakerOf(provider);
      if (!breaker.allowRequest()) {
        continue;
      }

      try {
        QuoteSnapshot quote = provider.getQuote(stockCode);
        if (quote != null) {
          breaker.onSuccess();
          return quote;
        }
        breaker.onFailure();
        providerFallbackCounter.increment();
      } catch (Exception e) {
        breaker.onFailure();
        providerFallbackCounter.increment();
        log.warn(
            "Provider {} getQuote failed for {}: {}",
            provider.getClass().getSimpleName(),
            stockCode,
            e.getMessage());
      }
    }
    return null;
  }

  private Map<String, QuoteSnapshot> loadBatchFromProviders(List<String> stockCodes) {
    for (MarketDataProvider provider : providers) {
      ProviderCircuitBreaker breaker = circuitBreakerOf(provider);
      if (!breaker.allowRequest()) {
        continue;
      }

      try {
        List<QuoteSnapshot> quotes = provider.batchGetQuotes(stockCodes);
        if (quotes == null || quotes.isEmpty()) {
          breaker.onFailure();
          providerFallbackCounter.increment();
          continue;
        }

        breaker.onSuccess();
        Map<String, QuoteSnapshot> mapped = new LinkedHashMap<>();
        for (QuoteSnapshot quote : quotes) {
          if (quote == null || quote.getStockCode() == null) {
            continue;
          }
          mapped.put(normalizeStockCode(quote.getStockCode()), quote);
        }
        return mapped;
      } catch (Exception e) {
        breaker.onFailure();
        providerFallbackCounter.increment();
        log.warn(
            "Provider {} batchGetQuotes failed for {} codes: {}",
            provider.getClass().getSimpleName(),
            stockCodes.size(),
            e.getMessage());
      }
    }
    return Map.of();
  }

  private ProviderCircuitBreaker circuitBreakerOf(MarketDataProvider provider) {
    String providerKey = provider.getClass().getName();
    return circuitBreakers.computeIfAbsent(
        providerKey, key -> new ProviderCircuitBreaker(3, Duration.ofSeconds(30)));
  }
}
