package com.lzbsdsg.stocksimulation.market.infrastructure.gateway;

import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 行情缓存网关（Redis）
 *
 * <p>L1 缓存：行情快照 TTL=5s，K线 TTL=60s
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketCacheGateway {

  private final RedisTemplate<String, Object> redisTemplate;

  private static final String QUOTE_KEY_PREFIX = "market:quote:";
  private static final String KLINE_KEY_PREFIX = "market:kline:";
  private static final long QUOTE_TTL_SECONDS = 5;
  private static final long KLINE_TTL_SECONDS = 60;

  /** 缓存行情快照 */
  public void cacheQuote(String stockCode, Object quoteSnapshot) {
    redisTemplate
        .opsForValue()
        .set(QUOTE_KEY_PREFIX + stockCode, quoteSnapshot, QUOTE_TTL_SECONDS, TimeUnit.SECONDS);
  }

  /** 获取缓存的行情快照 */
  public Object getCachedQuote(String stockCode) {
    return redisTemplate.opsForValue().get(QUOTE_KEY_PREFIX + stockCode);
  }

  /** 缓存K线数据 */
  public void cacheKLine(String key, Object kLineData) {
    redisTemplate
        .opsForValue()
        .set(KLINE_KEY_PREFIX + key, kLineData, KLINE_TTL_SECONDS, TimeUnit.SECONDS);
  }

  /** 获取缓存的K线数据 */
  public Object getCachedKLine(String key) {
    return redisTemplate.opsForValue().get(KLINE_KEY_PREFIX + key);
  }

  /** 节流检查：同一股票 3s 内重复请求直接返回缓存 */
  public boolean isThrottled(String stockCode) {
    return Boolean.TRUE.equals(redisTemplate.hasKey(QUOTE_KEY_PREFIX + stockCode));
  }
}
