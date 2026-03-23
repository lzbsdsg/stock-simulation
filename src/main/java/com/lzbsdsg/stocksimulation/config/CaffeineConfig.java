package com.lzbsdsg.stocksimulation.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Caffeine 本地缓存配置（L1 缓存）。
 *
 * <p>多 region 配置： - quote: 行情快照, TTL=3s, max=5000 (命中率目标 >80%) - stock: 股票基础信息, TTL=5min, max=6000
 * - config: 系统配置/交易规则, TTL=10min, max=200 - loginLock: 登录锁定状态, TTL=30min, max=10000
 *
 * <p>统计: recordStats() → Micrometer 采集命中率 caffeine_hit_rate Gauge
 */
@Configuration
public class CaffeineConfig {

  public static final String CACHE_QUOTE = "quote";
  public static final String CACHE_STOCK = "stock";
  public static final String CACHE_CONFIG = "config";
  public static final String CACHE_LOGIN_LOCK = "loginLock";

  @Bean
  public CacheManager cacheManager() {
    CaffeineCacheManager manager = new CaffeineCacheManager();
    manager.setAllowNullValues(false);
    manager.registerCustomCache(
        CACHE_QUOTE,
        Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(3))
            .maximumSize(5000)
            .recordStats()
            .build());
    manager.registerCustomCache(
        CACHE_STOCK,
        Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(6000)
            .recordStats()
            .build());
    manager.registerCustomCache(
        CACHE_CONFIG,
        Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .maximumSize(200)
            .recordStats()
            .build());
    manager.registerCustomCache(
        CACHE_LOGIN_LOCK,
        Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(30))
            .maximumSize(10000)
            .recordStats()
            .build());
    return manager;
  }
}
