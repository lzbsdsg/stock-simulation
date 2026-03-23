package com.lzbsdsg.stocksimulation.market.infrastructure.gateway;

import com.lzbsdsg.stocksimulation.config.CaffeineConfig;
import com.lzbsdsg.stocksimulation.market.domain.entity.KLinePoint;
import com.lzbsdsg.stocksimulation.market.domain.entity.QuoteSnapshot;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 行情缓存网关（Redis）
 *
 * <p>L1 缓存：行情快照 TTL=5s，K线 TTL=60s
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketCacheGateway {

  public static final String CACHE_STATUS_HEADER = "X-Cache-Status";
  public static final String HIT_L1 = "HIT-L1";
  public static final String HIT_L2 = "HIT-L2";
  public static final String MISS = "MISS";
  public static final String STALE = "STALE";

  private static final String NULL_SENTINEL = "__NULL_QUOTE__";

  private final CacheManager cacheManager;
  private final RedisTemplate<String, Object> redisTemplate;
  private final Random random = new Random();

  private static final String QUOTE_KEY_PREFIX = "market:quote:";
  private static final String QUOTE_STALE_KEY_PREFIX = "market:quote:stale:";
  private static final String KLINE_KEY_PREFIX = "market:kline:";
  private static final String LOAD_LOCK_KEY_PREFIX = "market:load:";
  private static final long QUOTE_TTL_MILLIS = 5000;
  private static final long QUOTE_JITTER_MAX_MILLIS = 500;
  private static final long NULL_QUOTE_TTL_SECONDS = 30;
  private static final long KLINE_TTL_SECONDS = 60;
  private static final long STALE_QUOTE_TTL_SECONDS = 300;
  private static final long LOAD_LOCK_TTL_SECONDS = 3;

  public CacheResult<QuoteSnapshot> getQuote(String stockCode) {
    String normalizedCode = normalizeStockCode(stockCode);
    Cache quoteCache = getQuoteCache();
    Object l1 = quoteCache.get(normalizedCode, Object.class);
    if (l1 != null) {
      if (isNullSentinel(l1)) {
        return CacheResult.nullHit(HIT_L1);
      }
      return CacheResult.hit(castQuote(l1), HIT_L1);
    }

    Object l2 = redisTemplate.opsForValue().get(QUOTE_KEY_PREFIX + normalizedCode);
    if (l2 != null) {
      quoteCache.put(normalizedCode, l2);
      if (isNullSentinel(l2)) {
        return CacheResult.nullHit(HIT_L2);
      }
      return CacheResult.hit(castQuote(l2), HIT_L2);
    }

    return CacheResult.miss();
  }

  public void cacheQuote(String stockCode, QuoteSnapshot quoteSnapshot) {
    String normalizedCode = normalizeStockCode(stockCode);
    Cache quoteCache = getQuoteCache();
    quoteCache.put(normalizedCode, quoteSnapshot);
    redisTemplate
        .opsForValue()
        .set(
            QUOTE_KEY_PREFIX + normalizedCode,
            quoteSnapshot,
            QUOTE_TTL_MILLIS + random.nextInt((int) QUOTE_JITTER_MAX_MILLIS + 1),
            TimeUnit.MILLISECONDS);
    redisTemplate
        .opsForValue()
        .set(
            QUOTE_STALE_KEY_PREFIX + normalizedCode,
            quoteSnapshot,
            STALE_QUOTE_TTL_SECONDS,
            TimeUnit.SECONDS);
  }

  public void cacheNullQuote(String stockCode) {
    String normalizedCode = normalizeStockCode(stockCode);
    Cache quoteCache = getQuoteCache();
    quoteCache.put(normalizedCode, NULL_SENTINEL);
    redisTemplate
        .opsForValue()
        .set(
            QUOTE_KEY_PREFIX + normalizedCode,
            NULL_SENTINEL,
            NULL_QUOTE_TTL_SECONDS,
            TimeUnit.SECONDS);
  }

  public QuoteSnapshot getStaleQuote(String stockCode) {
    Object stale = redisTemplate.opsForValue().get(QUOTE_STALE_KEY_PREFIX + normalizeStockCode(stockCode));
    return castQuote(stale);
  }

  public boolean tryAcquireLoadLock(String stockCode) {
    Boolean acquired =
        redisTemplate
            .opsForValue()
            .setIfAbsent(
                LOAD_LOCK_KEY_PREFIX + normalizeStockCode(stockCode),
                "1",
                LOAD_LOCK_TTL_SECONDS,
                TimeUnit.SECONDS);
    return Boolean.TRUE.equals(acquired);
  }

  public void releaseLoadLock(String stockCode) {
    redisTemplate.delete(LOAD_LOCK_KEY_PREFIX + normalizeStockCode(stockCode));
  }

  public void cacheKLine(String key, List<KLinePoint> kLineData) {
    redisTemplate
        .opsForValue()
        .set(KLINE_KEY_PREFIX + key, kLineData, KLINE_TTL_SECONDS, TimeUnit.SECONDS);
  }

  @SuppressWarnings("unchecked")
  public List<KLinePoint> getCachedKLine(String key) {
    Object cached = redisTemplate.opsForValue().get(KLINE_KEY_PREFIX + key);
    if (cached == null) {
      return null;
    }
    return (List<KLinePoint>) cached;
  }

  public void setCacheStatusHeader(String status) {
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

  private String normalizeStockCode(String stockCode) {
    return stockCode == null ? "" : stockCode.trim().toLowerCase();
  }

  private Cache getQuoteCache() {
    Cache cache = cacheManager.getCache(CaffeineConfig.CACHE_QUOTE);
    if (cache == null) {
      throw new IllegalStateException("quote cache region not configured");
    }
    return cache;
  }

  private boolean isNullSentinel(Object value) {
    return NULL_SENTINEL.equals(value);
  }

  private QuoteSnapshot castQuote(Object value) {
    if (value instanceof QuoteSnapshot quoteSnapshot) {
      return quoteSnapshot;
    }
    return null;
  }

  public record CacheResult<T>(T value, String status, boolean hit, boolean nullValue) {

    public static <T> CacheResult<T> hit(T value, String status) {
      return new CacheResult<>(value, status, true, false);
    }

    public static <T> CacheResult<T> nullHit(String status) {
      return new CacheResult<>(null, status, true, true);
    }

    public static <T> CacheResult<T> miss() {
      return new CacheResult<>(null, MISS, false, false);
    }
  }
}
