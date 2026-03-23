package com.lzbsdsg.stocksimulation.config;

/**
 * Caffeine 本地缓存配置（L1 缓存）。
 *
 * <p>多 region 配置： - quote: 行情快照, TTL=3s, max=5000 (命中率目标 >80%) - stock: 股票基础信息, TTL=5min, max=6000
 * - config: 系统配置/交易规则, TTL=10min, max=200 - loginLock: 登录锁定状态, TTL=30min, max=10000
 *
 * <p>统计: recordStats() → Micrometer 采集命中率 caffeine_hit_rate Gauge
 */
public class CaffeineConfig {
  // TODO: @Configuration
  // TODO: @Bean CaffeineCacheManager 多 region
  // TODO: @Bean CacheMetricsRegistrar (Micrometer)
}
