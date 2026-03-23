package com.lzbsdsg.stocksimulation.market.infrastructure.ingest;

/**
 * 行情拉取主节点服务（分布式锁选主）。
 *
 * <p>核心机制： - Redis 分布式锁 key: market:ingest:leader, TTL=10s + 定时续期 - 仅持锁实例定时拉取行情（3s/次, @Scheduled） -
 * 拉取结果： 1. 写入 Redis L2 缓存 (TTL=5s+random) 2. 发布 Redis Pub/Sub channel: market:quote:broadcast -
 * 其他实例通过 MarketPubSubListener 订阅广播
 *
 * <p>扩展： - 多实例部署时仅一个实例拉取，减少上游 API 调用量 - 持锁实例宕机后，锁 TTL 过期，其他实例自动抢锁接管
 */
public class MarketIngestService {
  // TODO: @Service
  // TODO: @Scheduled(fixedRate = 3000) pullAndBroadcast()
  // TODO: Redis分布式锁(SETNX + TTL + watchdog续期)
  // TODO: 依赖 MarketDataProvider, RedisTemplate
}
