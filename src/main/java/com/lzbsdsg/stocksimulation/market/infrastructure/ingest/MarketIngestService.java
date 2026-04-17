package com.lzbsdsg.stocksimulation.market.infrastructure.ingest;

import com.lzbsdsg.stocksimulation.market.domain.entity.QuoteSnapshot;
import com.lzbsdsg.stocksimulation.market.domain.entity.StockInfo;
import com.lzbsdsg.stocksimulation.market.domain.gateway.MarketDataProvider;
import com.lzbsdsg.stocksimulation.market.domain.repository.StockInfoRepository;
import com.lzbsdsg.stocksimulation.market.domain.service.QuoteMergePolicy;
import com.lzbsdsg.stocksimulation.market.infrastructure.gateway.MarketCacheGateway;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 行情拉取主节点服务（分布式锁选主）。
 *
 * <p>
 * 核心机制： - Redis 分布式锁 key: market:ingest:leader, TTL=10s + 定时续期 -
 * 仅持锁实例定时拉取行情（3s/次, @Scheduled） -
 * 拉取结果： 1. 写入 Redis L2 缓存 (TTL=5s+random) 2. 发布 Redis Pub/Sub channel:
 * market:quote:broadcast -
 * 其他实例通过 MarketPubSubListener 订阅广播
 *
 * <p>
 * 扩展： - 多实例部署时仅一个实例拉取，减少上游 API 调用量 - 持锁实例宕机后，锁 TTL 过期，其他实例自动抢锁接管
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketIngestService {

  static final String INGEST_LEADER_KEY = "market:ingest:leader";
  static final String BROADCAST_CHANNEL = "market:quote:broadcast";
  static final String PUB_TS_KEY_PREFIX = "market:ingest:pubts:";
  static final Duration LEADER_LOCK_TTL = Duration.ofSeconds(10);

  private static final int DEFAULT_ACTIVE_BATCH_SIZE = 800;
  private static final int DEFAULT_ROUND_ROBIN_BATCH_SIZE = 100;
  private static final long DEFAULT_ACTIVE_WINDOW_MS = 8000L;
  private static final long DEFAULT_STOCK_UNIVERSE_REFRESH_MS = 300000L;
  private static final long PROVIDER_BATCH_TIMEOUT_MS = 1400L;

  private static final String INGEST_CYCLE_TIMER_METRIC = "market.ingest.cycle.duration";

  private final List<MarketDataProvider> providers;
  private final StockInfoRepository stockInfoRepository;
  private final MarketCacheGateway marketCacheGateway;
  private final RedisTemplate<String, Object> redisTemplate;
  private final MarketActiveQuoteRegistry marketActiveQuoteRegistry;
  private final MeterRegistry meterRegistry;

  @Value("${market.ingest.latency-sample-enabled:false}")
  private boolean latencySampleEnabled;

  @Value("${market.ingest.active-window-ms:8000}")
  private long activeWindowMs;

  @Value("${market.ingest.active-batch-size:800}")
  private int activeBatchSize;

  @Value("${market.ingest.round-robin-batch-size:100}")
  private int roundRobinBatchSize;

  @Value("${market.ingest.stock-universe-refresh-ms:300000}")
  private long stockUniverseRefreshMs;

  private volatile String leaderToken;
  private volatile List<String> stockUniverse = List.of();
  private volatile long stockUniverseLoadedAtMs;
  private final AtomicInteger roundRobinCursor = new AtomicInteger(0);
  private volatile long lastIngestAtMs;
  private volatile long lastIngestDurationMs;
  private volatile int lastIngestCodeCount;
  private volatile int lastPublishedQuoteCount;
  private Timer ingestCycleTimer;

  @PostConstruct
  void initMetrics() {
    ingestCycleTimer = Timer.builder(INGEST_CYCLE_TIMER_METRIC)
        .description("Market ingest cycle duration")
        .register(meterRegistry);
  }

  @Scheduled(fixedRateString = "${market.ingest.pull-interval-ms:1000}")
  public void pullAndBroadcast() {
    long cycleStartNs = System.nanoTime();
    if (!ensureLeadership()) {
      return;
    }

    int ingestCodeCount = 0;
    int publishedQuoteCount = 0;
    try {
      marketActiveQuoteRegistry.evictStale(Duration.ofMillis(Math.max(activeWindowMs, 1L)));
      List<String> stockCodes = loadIngestCodes();
      ingestCodeCount = stockCodes.size();
      if (stockCodes.isEmpty()) {
        return;
      }

      List<QuoteSnapshot> quotes = loadQuotes(stockCodes);
      for (QuoteSnapshot quote : quotes) {
        if (quote == null || quote.getStockCode() == null || quote.getStockCode().isBlank()) {
          continue;
        }
        String normalizedCode = quote.getStockCode().trim().toLowerCase();
        boolean changed = marketCacheGateway.cacheQuoteIfFresh(normalizedCode, quote);
        if (!changed) {
          continue;
        }
        if (latencySampleEnabled) {
          long publishTs = System.currentTimeMillis();
          redisTemplate
              .opsForValue()
              .set(PUB_TS_KEY_PREFIX + normalizedCode, publishTs, 30, TimeUnit.SECONDS);
        }
        redisTemplate.convertAndSend(BROADCAST_CHANNEL, quote);
        publishedQuoteCount++;
      }
    } finally {
      if (ingestCycleTimer == null) {
        initMetrics();
      }
      lastIngestAtMs = System.currentTimeMillis();
      long durationNs = System.nanoTime() - cycleStartNs;
      lastIngestDurationMs = TimeUnit.NANOSECONDS.toMillis(durationNs);
      lastIngestCodeCount = ingestCodeCount;
      lastPublishedQuoteCount = publishedQuoteCount;
      ingestCycleTimer.record(durationNs, TimeUnit.NANOSECONDS);
    }
  }

  @Scheduled(fixedRateString = "${market.ingest.renew-interval-ms:3000}")
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
    Boolean acquired = redisTemplate
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

  List<String> loadIngestCodes() {
    int safeActiveBatchSize = Math.max(activeBatchSize, 0);
    int safeRoundRobinBatchSize = Math.max(roundRobinBatchSize, 0);
    Duration activeWindow = Duration.ofMillis(Math.max(activeWindowMs, DEFAULT_ACTIVE_WINDOW_MS));

    List<String> activeCodes = marketActiveQuoteRegistry.listActiveCodes(
        activeWindow,
        safeActiveBatchSize > 0 ? safeActiveBatchSize : DEFAULT_ACTIVE_BATCH_SIZE);
    Set<String> activeSet = new HashSet<>(activeCodes);

    List<String> fallbackCodes = pickRoundRobinCodes(
        activeSet,
        safeRoundRobinBatchSize > 0
            ? safeRoundRobinBatchSize
            : DEFAULT_ROUND_ROBIN_BATCH_SIZE);

    List<String> ingestCodes = new ArrayList<>(activeCodes.size() + fallbackCodes.size());
    ingestCodes.addAll(activeCodes);
    ingestCodes.addAll(fallbackCodes);
    return ingestCodes;
  }

  private List<String> pickRoundRobinCodes(Set<String> excludedCodes, int batchSize) {
    List<String> universe = getOrLoadStockUniverse();
    if (universe.isEmpty() || batchSize <= 0) {
      return List.of();
    }

    int size = universe.size();
    int start = Math.floorMod(roundRobinCursor.getAndAdd(batchSize), size);
    List<String> result = new ArrayList<>(batchSize);
    for (int i = 0; i < size && result.size() < batchSize; i++) {
      String code = universe.get((start + i) % size);
      if (!excludedCodes.contains(code)) {
        result.add(code);
      }
    }
    return result;
  }

  private List<String> getOrLoadStockUniverse() {
    long now = System.currentTimeMillis();
    if (!stockUniverse.isEmpty() && now - stockUniverseLoadedAtMs < Math.max(stockUniverseRefreshMs, 1L)) {
      return stockUniverse;
    }

    List<String> loaded = stockInfoRepository.findAllListed().stream()
        .map(StockInfo::getStockCode)
        .filter(code -> code != null && !code.isBlank())
        .map(code -> code.trim().toLowerCase(Locale.ROOT))
        .toList();
    stockUniverse = loaded;
    stockUniverseLoadedAtMs = now;
    return stockUniverse;
  }

  private List<QuoteSnapshot> loadQuotes(List<String> stockCodes) {
    List<CompletableFuture<List<QuoteSnapshot>>> futures = new ArrayList<>();
    for (MarketDataProvider provider : providers) {
      futures.add(
          CompletableFuture
              .supplyAsync(() -> fetchProviderBatch(provider, stockCodes))
              .completeOnTimeout(List.of(), PROVIDER_BATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
              .exceptionally(_ex -> List.of()));
    }

    Map<String, QuoteSnapshot> merged = new HashMap<>();
    for (CompletableFuture<List<QuoteSnapshot>> future : futures) {
      List<QuoteSnapshot> quotes = future.join();
      for (QuoteSnapshot quote : quotes) {
        if (quote == null || quote.getStockCode() == null || quote.getStockCode().isBlank()) {
          continue;
        }
        String normalizedCode = quote.getStockCode().trim().toLowerCase(Locale.ROOT);
        QuoteSnapshot current = merged.get(normalizedCode);
        if (QuoteMergePolicy.shouldReplace(current, quote)) {
          merged.put(normalizedCode, quote);
        }
      }
    }

    if (merged.isEmpty()) {
      return List.of();
    }

    List<QuoteSnapshot> ordered = new ArrayList<>();
    for (String code : stockCodes) {
      QuoteSnapshot quote = merged.get(code);
      if (quote != null) {
        ordered.add(quote);
      }
    }
    return ordered;
  }

  private List<QuoteSnapshot> fetchProviderBatch(MarketDataProvider provider, List<String> stockCodes) {
    try {
      List<QuoteSnapshot> quotes = provider.batchGetQuotes(stockCodes);
      return quotes == null ? List.of() : quotes;
    } catch (Exception ex) {
      log.warn(
          "Provider {} batch ingest failed: {}",
          provider.getClass().getSimpleName(),
          ex.getMessage());
      return List.of();
    }
  }

  public long getLastIngestAtMs() {
    return lastIngestAtMs;
  }

  public long getLastIngestDurationMs() {
    return lastIngestDurationMs;
  }

  public int getLastIngestCodeCount() {
    return lastIngestCodeCount;
  }

  public int getLastPublishedQuoteCount() {
    return lastPublishedQuoteCount;
  }
}
