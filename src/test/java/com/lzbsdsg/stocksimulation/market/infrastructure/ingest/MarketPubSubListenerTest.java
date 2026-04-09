package com.lzbsdsg.stocksimulation.market.infrastructure.ingest;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lzbsdsg.stocksimulation.config.CaffeineConfig;
import com.lzbsdsg.stocksimulation.market.domain.entity.QuoteSnapshot;
import com.lzbsdsg.stocksimulation.market.infrastructure.websocket.MarketWebSocketHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

class MarketPubSubListenerTest {

  @Test
  void should_update_l1_and_push_ws_when_receive_broadcast() {
    CacheManager cacheManager = new CaffeineConfig().cacheManager();
    MarketWebSocketHandler websocketHandler = org.mockito.Mockito.mock(MarketWebSocketHandler.class);
    RedisSerializer<Object> serializer = org.mockito.Mockito.mock(RedisSerializer.class);
    RedisTemplate<String, Object> redisTemplate = org.mockito.Mockito.mock(RedisTemplate.class);
    doReturn(serializer).when(redisTemplate).getValueSerializer();
    ObjectMapper objectMapper = new ObjectMapper();
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    MarketPubSubListener listener = new MarketPubSubListener(cacheManager, websocketHandler, redisTemplate,
        objectMapper, meterRegistry);

    QuoteSnapshot quote = new QuoteSnapshot();
    quote.setStockCode("sh600519");
    quote.setStockName("贵州茅台");
    quote.setCurrentPrice(new BigDecimal("1688.88"));
    quote.setTimestamp(LocalDateTime.now());

    Message message = new DefaultMessage(
        "market:quote:broadcast".getBytes(StandardCharsets.UTF_8), "payload".getBytes(StandardCharsets.UTF_8));
    when(serializer.deserialize("payload".getBytes(StandardCharsets.UTF_8))).thenReturn(quote);

    listener.onMessage(message, null);

    Object cached = cacheManager.getCache(CaffeineConfig.CACHE_QUOTE).get("sh600519", Object.class);
    assertNotNull(cached);
    verify(websocketHandler).pushQuote("sh600519", quote);
  }

  @Test
  void should_ignore_malformed_message() {
    CacheManager cacheManager = new CaffeineConfig().cacheManager();
    MarketWebSocketHandler websocketHandler = org.mockito.Mockito.mock(MarketWebSocketHandler.class);
    RedisSerializer<Object> serializer = org.mockito.Mockito.mock(RedisSerializer.class);
    RedisTemplate<String, Object> redisTemplate = org.mockito.Mockito.mock(RedisTemplate.class);
    doReturn(serializer).when(redisTemplate).getValueSerializer();
    ObjectMapper objectMapper = new ObjectMapper();
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    MarketPubSubListener listener = new MarketPubSubListener(cacheManager, websocketHandler, redisTemplate,
        objectMapper, meterRegistry);

    Message message = new DefaultMessage(
        "market:quote:broadcast".getBytes(StandardCharsets.UTF_8), "bad-json".getBytes(StandardCharsets.UTF_8));
    when(serializer.deserialize("bad-json".getBytes(StandardCharsets.UTF_8)))
        .thenThrow(new RuntimeException("bad payload"));

    listener.onMessage(message, null);

    verify(websocketHandler, never()).pushQuote(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }
}
