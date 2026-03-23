package com.lzbsdsg.stocksimulation.market.domain.service;

import com.lzbsdsg.stocksimulation.common.exception.BizException;
import com.lzbsdsg.stocksimulation.common.result.ErrorCode;
import com.lzbsdsg.stocksimulation.market.domain.entity.KLinePeriod;
import com.lzbsdsg.stocksimulation.market.domain.entity.KLinePoint;
import com.lzbsdsg.stocksimulation.market.domain.entity.QuoteSnapshot;
import com.lzbsdsg.stocksimulation.market.domain.gateway.MarketDataProvider;
import com.lzbsdsg.stocksimulation.market.infrastructure.gateway.MarketCacheGateway;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 行情数据门面（领域服务）
 *
 * <p>负责协调 Provider + 缓存 + 降级策略
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataFacade {

  private final List<MarketDataProvider> providers;
  private final MarketCacheGateway marketCacheGateway;

  /** 获取单只股票行情（先缓存 → Provider → 降级） */
  public QuoteSnapshot getQuote(String stockCode) {
    String normalizedCode = normalizeStockCode(stockCode);
    MarketCacheGateway.CacheResult<QuoteSnapshot> cacheResult = marketCacheGateway.getQuote(normalizedCode);
    if (cacheResult.hit()) {
      marketCacheGateway.setCacheStatusHeader(cacheResult.status());
      return cacheResult.value();
    }

    boolean acquiredLock = marketCacheGateway.tryAcquireLoadLock(normalizedCode);
    if (!acquiredLock) {
      sleepSilently(80);
      MarketCacheGateway.CacheResult<QuoteSnapshot> lockWaitResult = marketCacheGateway.getQuote(normalizedCode);
      if (lockWaitResult.hit()) {
        marketCacheGateway.setCacheStatusHeader(lockWaitResult.status());
        return lockWaitResult.value();
      }
    }

    try {
      for (MarketDataProvider provider : providers) {
        if (provider.isAvailable()) {
          try {
            QuoteSnapshot quote = provider.getQuote(normalizedCode);
            if (quote != null) {
              marketCacheGateway.cacheQuote(normalizedCode, quote);
              marketCacheGateway.setCacheStatusHeader(MarketCacheGateway.MISS);
              return quote;
            }
          } catch (Exception e) {
            log.warn(
                "Provider {} getQuote failed for {}: {}",
                provider.getClass().getSimpleName(),
                normalizedCode,
                e.getMessage());
          }
        }
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
    throw new BizException(ErrorCode.MARKET_DATA_UNAVAILABLE);
  }

  /** 批量获取行情 */
  public List<QuoteSnapshot> batchGetQuotes(List<String> stockCodes) {
    List<QuoteSnapshot> quotes = new ArrayList<>();
    for (String stockCode : stockCodes) {
      quotes.add(getQuote(stockCode));
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
    return stockCode == null ? "" : stockCode.trim().toLowerCase();
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
}
