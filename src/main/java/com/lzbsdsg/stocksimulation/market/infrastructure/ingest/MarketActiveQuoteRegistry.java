package com.lzbsdsg.stocksimulation.market.infrastructure.ingest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/** 活跃股票注册表：由前端上报当前可见股票集合，供行情拉取调度使用。 */
@Component
@RequiredArgsConstructor
public class MarketActiveQuoteRegistry {

  static final String ACTIVE_QUOTE_ZSET_KEY = "market:active:quotes";
  private static final int MAX_REPORT_SIZE = 200;

  private final RedisTemplate<String, Object> redisTemplate;

  public void reportVisibleCodes(List<String> stockCodes) {
    if (stockCodes == null || stockCodes.isEmpty()) {
      return;
    }

    long nowMillis = System.currentTimeMillis();
    Set<String> normalizedCodes = new LinkedHashSet<>();
    for (String stockCode : stockCodes) {
      String normalized = normalizeStockCode(stockCode);
      if (normalized.isBlank()) {
        continue;
      }
      normalizedCodes.add(normalized);
      if (normalizedCodes.size() >= MAX_REPORT_SIZE) {
        break;
      }
    }

    for (String stockCode : normalizedCodes) {
      redisTemplate.opsForZSet().add(ACTIVE_QUOTE_ZSET_KEY, stockCode, nowMillis);
    }
  }

  public List<String> listActiveCodes(Duration activeWindow, int limit) {
    if (limit <= 0) {
      return List.of();
    }
    long nowMillis = System.currentTimeMillis();
    long minScore = nowMillis - Math.max(activeWindow.toMillis(), 1L);
    Set<Object> rawCodes =
        redisTemplate
            .opsForZSet()
            .reverseRangeByScore(ACTIVE_QUOTE_ZSET_KEY, minScore, nowMillis, 0, limit);
    if (rawCodes == null || rawCodes.isEmpty()) {
      return List.of();
    }

    List<String> result = new ArrayList<>(rawCodes.size());
    for (Object rawCode : rawCodes) {
      if (rawCode == null) {
        continue;
      }
      String normalized = normalizeStockCode(String.valueOf(rawCode));
      if (!normalized.isBlank()) {
        result.add(normalized);
      }
    }
    return result;
  }

  public long countActiveCodes(Duration activeWindow) {
    long nowMillis = System.currentTimeMillis();
    long minScore = nowMillis - Math.max(activeWindow.toMillis(), 1L);
    Long count = redisTemplate.opsForZSet().count(ACTIVE_QUOTE_ZSET_KEY, minScore, nowMillis);
    return count == null ? 0L : count;
  }

  public void evictStale(Duration activeWindow) {
    long minScore = System.currentTimeMillis() - Math.max(activeWindow.toMillis(), 1L);
    redisTemplate.opsForZSet().removeRangeByScore(ACTIVE_QUOTE_ZSET_KEY, 0, minScore);
  }

  String normalizeStockCode(String stockCode) {
    if (stockCode == null) {
      return "";
    }
    String code = stockCode.trim().toLowerCase(Locale.ROOT);
    if (code.isBlank()) {
      return "";
    }
    if (code.startsWith("sh") || code.startsWith("sz") || code.startsWith("bj")) {
      return code;
    }
    if (code.matches("^[569]\\d{5}$")) {
      return "sh" + code;
    }
    if (code.matches("^[03]\\d{5}$")) {
      return "sz" + code;
    }
    if (code.matches("^[48]\\d{5}$")) {
      return "bj" + code;
    }
    return code;
  }
}
