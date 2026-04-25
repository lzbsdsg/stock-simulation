package com.lzbsdsg.stocksimulation.market.infrastructure.gateway;

import com.lzbsdsg.stocksimulation.config.CaffeineConfig;
import com.lzbsdsg.stocksimulation.market.domain.entity.KLinePoint;
import com.lzbsdsg.stocksimulation.market.domain.entity.QuoteSnapshot;
import com.lzbsdsg.stocksimulation.market.domain.service.QuoteMergePolicy;
import jakarta.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 * <p>L1 缓存：行情快照 TTL=3s，K 线 TTL=15s；L2 Redis：K 线 TTL=60s
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
  private static final long QUOTE_LOAD_LOCK_TTL_SECONDS = 3;
  private static final long KLINE_LOAD_LOCK_TTL_SECONDS = 30;

  public CacheResult<QuoteSnapshot> getQuote(String stockCode) {
    String normalizedCode = normalizeStockCode(stockCode);
    Cache quoteCache = getQuoteCache();
    Object l1 = quoteCache.get(normalizedCode, Object.class);
    if (l1 != null) {
      if (isNullSentinel(l1)) {
        return CacheResult.nullHit(HIT_L1);
      }
      try {
        return CacheResult.hit(toQuoteSnapshot(l1), HIT_L1);
      } catch (Exception ex) {
        log.warn(
            "Invalid quote L1 cache format for code={}, fallback to L2: {}",
            normalizedCode,
            ex.getMessage());
        quoteCache.evict(normalizedCode);
      }
    }

    Object l2 = redisTemplate.opsForValue().get(QUOTE_KEY_PREFIX + normalizedCode);
    if (l2 != null) {
      if (isNullSentinel(l2)) {
        quoteCache.put(normalizedCode, l2);
        return CacheResult.nullHit(HIT_L2);
      }
      try {
        QuoteSnapshot converted = toQuoteSnapshot(l2);
        quoteCache.put(normalizedCode, converted);
        return CacheResult.hit(converted, HIT_L2);
      } catch (Exception ex) {
        log.warn(
            "Invalid quote L2 cache format for code={}, fallback to miss: {}",
            normalizedCode,
            ex.getMessage());
        redisTemplate.delete(QUOTE_KEY_PREFIX + normalizedCode);
      }
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

  /**
   * 条件覆盖写缓存：仅当候选快照更新（更新或更完整）时才写入。
   *
   * @return true 表示行情发生有效变化，建议继续广播推送；false 表示无需推送。
   */
  public boolean cacheQuoteIfFresh(String stockCode, QuoteSnapshot candidate) {
    if (candidate == null) {
      return false;
    }

    String normalizedCode = normalizeStockCode(stockCode);
    QuoteSnapshot existing = getQuote(normalizedCode).value();
    if (!QuoteMergePolicy.shouldReplace(existing, candidate)) {
      return false;
    }

    cacheQuote(normalizedCode, candidate);
    if (existing == null) {
      return true;
    }
    return QuoteMergePolicy.isMeaningfulChange(existing, candidate);
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
    Object stale =
        redisTemplate.opsForValue().get(QUOTE_STALE_KEY_PREFIX + normalizeStockCode(stockCode));
    return castQuote(stale);
  }

  public boolean tryAcquireLoadLock(String stockCode) {
    return tryAcquireLoadLock(stockCode, QUOTE_LOAD_LOCK_TTL_SECONDS);
  }

  public boolean tryAcquireKLineLoadLock(String cacheKey) {
    return tryAcquireLoadLock(cacheKey, KLINE_LOAD_LOCK_TTL_SECONDS);
  }

  public boolean tryAcquireLoadLock(String stockCode, long ttlSeconds) {
    Boolean acquired =
        redisTemplate
            .opsForValue()
            .setIfAbsent(
                LOAD_LOCK_KEY_PREFIX + normalizeStockCode(stockCode),
                "1",
                ttlSeconds,
                TimeUnit.SECONDS);
    return Boolean.TRUE.equals(acquired);
  }

  public void releaseLoadLock(String stockCode) {
    redisTemplate.delete(LOAD_LOCK_KEY_PREFIX + normalizeStockCode(stockCode));
  }

  public void cacheKLine(String key, List<KLinePoint> kLineData) {
    List<KLinePoint> cachedValue = List.copyOf(kLineData);
    getKLineCache().put(key, cachedValue);
    redisTemplate
        .opsForValue()
        .set(KLINE_KEY_PREFIX + key, cachedValue, KLINE_TTL_SECONDS, TimeUnit.SECONDS);
  }

  public List<KLinePoint> getCachedKLineL1Only(String key) {
    Cache kLineCache = getKLineCache();
    Object l1 = kLineCache.get(key, Object.class);
    if (l1 == null) {
      return null;
    }
    try {
      return toKLinePointList(l1);
    } catch (Exception ex) {
      log.warn(
          "Invalid kline L1 cache format for key={}, fallback to miss: {}", key, ex.getMessage());
      kLineCache.evict(key);
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  public List<KLinePoint> getCachedKLine(String key) {
    Cache kLineCache = getKLineCache();
    Object l1 = kLineCache.get(key, Object.class);
    if (l1 != null) {
      try {
        return toKLinePointList(l1);
      } catch (Exception ex) {
        log.warn(
            "Invalid kline L1 cache format for key={}, fallback to L2: {}", key, ex.getMessage());
        kLineCache.evict(key);
      }
    }

    String redisKey = KLINE_KEY_PREFIX + key;
    Object cached = redisTemplate.opsForValue().get(redisKey);
    if (cached == null) {
      return null;
    }
    try {
      List<KLinePoint> converted = toKLinePointList(cached);
      kLineCache.put(key, converted);
      return converted;
    } catch (Exception ex) {
      log.warn(
          "Invalid kline cache format for key={}, fallback to miss: {}", redisKey, ex.getMessage());
      kLineCache.evict(key);
      redisTemplate.delete(redisKey);
      return null;
    }
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

  private Cache getKLineCache() {
    Cache cache = cacheManager.getCache(CaffeineConfig.CACHE_KLINE);
    if (cache == null) {
      throw new IllegalStateException("kline cache region not configured");
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

  private QuoteSnapshot toQuoteSnapshot(Object cached) {
    if (cached instanceof QuoteSnapshot quoteSnapshot) {
      return quoteSnapshot;
    }
    if (cached instanceof Map<?, ?> mapItem) {
      return fromQuoteMap(mapItem);
    }
    throw new IllegalStateException("quote cache type unsupported: " + cached.getClass().getName());
  }

  private List<KLinePoint> toKLinePointList(Object cached) {
    if (!(cached instanceof List<?> rawList)) {
      throw new IllegalStateException("kline cache is not a list");
    }

    List<KLinePoint> converted = new ArrayList<>(rawList.size());
    for (Object item : rawList) {
      if (item instanceof KLinePoint kLinePoint) {
        converted.add(kLinePoint);
        continue;
      }
      if (item instanceof Map<?, ?> mapItem) {
        converted.add(fromMap(mapItem));
        continue;
      }
      throw new IllegalStateException("kline item type unsupported: " + item.getClass().getName());
    }
    return converted;
  }

  private KLinePoint fromMap(Map<?, ?> mapItem) {
    KLinePoint point = new KLinePoint();
    point.setDate(parseLocalDate(mapItem.get("date")));
    point.setOpen(parseBigDecimal(mapItem.get("open")));
    point.setClose(parseBigDecimal(mapItem.get("close")));
    point.setHigh(parseBigDecimal(mapItem.get("high")));
    point.setLow(parseBigDecimal(mapItem.get("low")));
    point.setVolume(parseLong(mapItem.get("volume")));
    point.setAmount(parseBigDecimal(mapItem.get("amount")));
    return point;
  }

  private QuoteSnapshot fromQuoteMap(Map<?, ?> mapItem) {
    QuoteSnapshot snapshot = new QuoteSnapshot();
    snapshot.setStockCode(parseString(mapItem.get("stockCode")));
    snapshot.setStockName(parseString(mapItem.get("stockName")));
    snapshot.setCurrentPrice(parseBigDecimal(mapItem.get("currentPrice")));
    snapshot.setOpenPrice(parseBigDecimal(mapItem.get("openPrice")));
    snapshot.setClosePrice(parseBigDecimal(mapItem.get("closePrice")));
    snapshot.setHighPrice(parseBigDecimal(mapItem.get("highPrice")));
    snapshot.setLowPrice(parseBigDecimal(mapItem.get("lowPrice")));
    snapshot.setVolume(parseLong(mapItem.get("volume")));
    snapshot.setAmount(parseBigDecimal(mapItem.get("amount")));
    snapshot.setChangePercent(parseBigDecimal(mapItem.get("changePercent")));
    snapshot.setUpperLimitPrice(parseBigDecimal(mapItem.get("upperLimitPrice")));
    snapshot.setLowerLimitPrice(parseBigDecimal(mapItem.get("lowerLimitPrice")));
    snapshot.setTimestamp(parseLocalDateTime(mapItem.get("timestamp")));
    snapshot.setSource(parseString(mapItem.get("source")));
    snapshot.setSourceTimestamp(parseLocalDateTime(mapItem.get("sourceTimestamp")));
    return snapshot;
  }

  private LocalDate parseLocalDate(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof LocalDate localDate) {
      return localDate;
    }
    String text = String.valueOf(value).trim();
    if (text.isBlank()) {
      return null;
    }
    return LocalDate.parse(text);
  }

  private LocalDateTime parseLocalDateTime(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof LocalDateTime localDateTime) {
      return localDateTime;
    }
    String text = String.valueOf(value).trim();
    if (text.isBlank()) {
      return null;
    }
    return LocalDateTime.parse(text);
  }

  private BigDecimal parseBigDecimal(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof BigDecimal bigDecimal) {
      return bigDecimal;
    }
    String text = String.valueOf(value).trim();
    if (text.isBlank()) {
      return null;
    }
    return new BigDecimal(text);
  }

  private Long parseLong(Object value) {
    if (value == null) {
      return 0L;
    }
    if (value instanceof Number number) {
      return number.longValue();
    }
    String text = String.valueOf(value).trim();
    if (text.isBlank()) {
      return 0L;
    }
    return Long.parseLong(text);
  }

  private String parseString(Object value) {
    if (value == null) {
      return null;
    }
    String text = String.valueOf(value).trim();
    return text.isBlank() ? null : text;
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
