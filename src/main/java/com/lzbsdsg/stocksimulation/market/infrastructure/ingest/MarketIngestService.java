package com.lzbsdsg.stocksimulation.market.infrastructure.ingest;

import com.lzbsdsg.stocksimulation.market.domain.entity.QuoteSnapshot;
import com.lzbsdsg.stocksimulation.market.domain.entity.StockInfo;
import com.lzbsdsg.stocksimulation.market.domain.gateway.MarketDataProvider;
import com.lzbsdsg.stocksimulation.market.domain.repository.StockInfoRepository;
import com.lzbsdsg.stocksimulation.market.infrastructure.gateway.MarketCacheGateway;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 行情拉取主节点服务（分布式锁选主）。
 *
 * <p>核心机制： - Redis 分布式锁 key: market:ingest:leader, TTL=10s + 定时续期 - 仅持锁实例定时拉取行情（3s/次, @Scheduled） -
 * 拉取结果： 1. 写入 Redis L2 缓存 (TTL=5s+random) 2. 发布 Redis Pub/Sub channel: market:quote:broadcast -
 * 其他实例通过 MarketPubSubListener 订阅广播
 *
 * <p>扩展： - 多实例部署时仅一个实例拉取，减少上游 API 调用量 - 持锁实例宕机后，锁 TTL 过期，其他实例自动抢锁接管
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketIngestService {

  static final String INGEST_LEADER_KEY = "market:ingest:leader";
  static final String BROADCAST_CHANNEL = "market:quote:broadcast";
  static final String PUB_TS_KEY_PREFIX = "market:ingest:pubts:";
  static final Duration LEADER_LOCK_TTL = Duration.ofSeconds(10);
  private static final int INGEST_BATCH_SIZE = 50;

  private final List<MarketDataProvider> providers;
  private final StockInfoRepository stockInfoRepository;
  private final MarketCacheGateway marketCacheGateway;
  private final RedisTemplate<String, Object> redisTemplate;

  @Value("${market.ingest.latency-sample-enabled:false}")
  private boolean latencySampleEnabled;

  private volatile String leaderToken;

  @Scheduled(fixedRate = 3000)
  public void pullAndBroadcast() {
    if (!ensureLeadership()) {
      return;
    }

    List<String> stockCodes = loadIngestCodes();
    if (stockCodes.isEmpty()) {
      return;
    }

    List<QuoteSnapshot> quotes = loadQuotes(stockCodes);
    for (QuoteSnapshot quote : quotes) {
      if (quote == null || quote.getStockCode() == null || quote.getStockCode().isBlank()) {
        continue;
      }
      String normalizedCode = quote.getStockCode().trim().toLowerCase();
      marketCacheGateway.cacheQuote(normalizedCode, quote);
      if (latencySampleEnabled) {
        long publishTs = System.currentTimeMillis();
        redisTemplate
            .opsForValue()
            .set(PUB_TS_KEY_PREFIX + normalizedCode, publishTs, 30, TimeUnit.SECONDS);
      }
      redisTemplate.convertAndSend(BROADCAST_CHANNEL, quote);
    }
  }

  @Scheduled(fixedRate = 3000)
  public void renewLeadership() {
    String token = leaderToken;
    if (token == null) {
      return;
    }
    Object owner = redisTemplate.opsForValue().get(INGEST_LEADER_KEY);
    if (!token.equals(owner)) {
      leaderToken = null;
      return;
    }
    redisTemplate.expire(INGEST_LEADER_KEY, LEADER_LOCK_TTL.getSeconds(), TimeUnit.SECONDS);
  }

  boolean ensureLeadership() {
    String token = leaderToken;
    if (token != null) {
      Object owner = redisTemplate.opsForValue().get(INGEST_LEADER_KEY);
      if (token.equals(owner)) {
        return true;
      }
      leaderToken = null;
    }

    String nextToken = UUID.randomUUID().toString();
    Boolean acquired =
        redisTemplate
            .opsForValue()
            .setIfAbsent(
                INGEST_LEADER_KEY,
                nextToken,
                LEADER_LOCK_TTL.getSeconds(),
                TimeUnit.SECONDS);
    if (Boolean.TRUE.equals(acquired)) {
      leaderToken = nextToken;
      return true;
    }
    return false;
  }

  private List<String> loadIngestCodes() {
    return stockInfoRepository.findAllListed().stream()
        .map(StockInfo::getStockCode)
        .filter(code -> code != null && !code.isBlank())
        .map(code -> code.trim().toLowerCase())
        .limit(INGEST_BATCH_SIZE)
        .toList();
  }

  private List<QuoteSnapshot> loadQuotes(List<String> stockCodes) {
    for (MarketDataProvider provider : providers) {
      try {
        List<QuoteSnapshot> quotes = provider.batchGetQuotes(stockCodes);
        if (quotes != null && !quotes.isEmpty()) {
          return quotes;
        }
      } catch (Exception ex) {
        log.warn(
            "Provider {} batch ingest failed: {}",
            provider.getClass().getSimpleName(),
            ex.getMessage());
      }
    }
    return List.of();
  }
}
