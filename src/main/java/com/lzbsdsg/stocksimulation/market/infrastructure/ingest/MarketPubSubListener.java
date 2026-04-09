package com.lzbsdsg.stocksimulation.market.infrastructure.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lzbsdsg.stocksimulation.config.CaffeineConfig;
import com.lzbsdsg.stocksimulation.market.domain.entity.QuoteSnapshot;
import com.lzbsdsg.stocksimulation.market.infrastructure.websocket.MarketWebSocketHandler;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Component;

/**
 * 行情 Pub/Sub 订阅监听器。 所有 App 实例订阅 Redis channel: market:quote:broadcast
 *
 * <p>
 * 收到广播消息后： 1. 反序列化行情数据 2. 更新本地 L1 Caffeine 缓存 3. 触发 MarketWebSocketHandler
 * 推送到该实例管理的 WS 连接
 *
 * <p>
 * 注意： - 序列化异常应捕获并忽略（日志记录），不影响其他消息处理 - 更新 L1 缓存时使用 putIfAbsent 语义，避免覆盖更新的数据
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketPubSubListener implements MessageListener {

  private static final String PUBSUB_FANOUT_TIMER_METRIC = "market.pubsub.fanout.delay";

  private final CacheManager cacheManager;
  private final MarketWebSocketHandler marketWebSocketHandler;
  private final RedisTemplate<String, Object> redisTemplate;
  private final ObjectMapper objectMapper;
  private final MeterRegistry meterRegistry;
  private final StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
  private Timer pubSubFanoutDelayTimer;

  @Value("${market.ingest.latency-sample-enabled:false}")
  private boolean latencySampleEnabled;

  @PostConstruct
  void initMetrics() {
    pubSubFanoutDelayTimer = Timer.builder(PUBSUB_FANOUT_TIMER_METRIC)
        .description("Delay from ingest publish to app pubsub consume")
        .register(meterRegistry);
  }

  @Override
  public void onMessage(Message message, byte[] pattern) {
    try {
      Object value = redisTemplate.getValueSerializer().deserialize(message.getBody());
      QuoteSnapshot quote;
      if (value instanceof QuoteSnapshot typedQuote) {
        quote = typedQuote;
      } else if (value != null) {
        quote = objectMapper.convertValue(value, QuoteSnapshot.class);
      } else {
        return;
      }
      if (quote.getStockCode() == null || quote.getStockCode().isBlank()) {
        return;
      }

      String stockCode = quote.getStockCode().trim().toLowerCase();
      Cache quoteCache = cacheManager.getCache(CaffeineConfig.CACHE_QUOTE);
      if (quoteCache != null) {
        quoteCache.put(stockCode, quote);
      }

      if (latencySampleEnabled) {
        Object publishTsValue = redisTemplate.opsForValue().get(MarketIngestService.PUB_TS_KEY_PREFIX + stockCode);
        if (publishTsValue != null) {
          long publishTs;
          if (publishTsValue instanceof Number number) {
            publishTs = number.longValue();
          } else {
            publishTs = Long.parseLong(String.valueOf(publishTsValue));
          }
          long delayMs = System.currentTimeMillis() - publishTs;
          if (pubSubFanoutDelayTimer == null) {
            initMetrics();
          }
          pubSubFanoutDelayTimer.record(
              Math.max(delayMs, 0L), java.util.concurrent.TimeUnit.MILLISECONDS);
          log.info("market.pubsub.fanout.delay stockCode={} delayMs={}", stockCode, delayMs);
        }
      }

      marketWebSocketHandler.pushQuote(stockCode, quote);
    } catch (Exception ex) {
      String channel = stringRedisSerializer.deserialize(message.getChannel());
      log.warn("Ignore malformed pub/sub message on channel {}: {}", channel, ex.getMessage());
    }
  }
}
