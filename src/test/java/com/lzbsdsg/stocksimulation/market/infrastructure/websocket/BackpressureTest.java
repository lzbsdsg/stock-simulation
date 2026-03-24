package com.lzbsdsg.stocksimulation.market.infrastructure.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class BackpressureTest {

  private SimpMessagingTemplate messagingTemplate;
  private SimpleMeterRegistry meterRegistry;

  @BeforeEach
  void setUp() {
    messagingTemplate = Mockito.mock(SimpMessagingTemplate.class);
    meterRegistry = new SimpleMeterRegistry();
  }

  @Test
  void should_drop_oldest_when_queue_depth_over_limit() {
    MarketWebSocketSessionRegistry sessionRegistry =
      new MarketWebSocketSessionRegistry(meterRegistry, 10);
    MarketWebSocketHandler handler =
        new MarketWebSocketHandler(
        messagingTemplate,
        new ObjectMapper(),
        sessionRegistry,
        meterRegistry,
        1,
        65536,
        1,
        10,
        5000);

    handler.pushQuote("sh600519", Map.of("p", 1));
    handler.pushQuote("sh600520", Map.of("p", 2));

    assertEquals(1, handler.getQueuedTaskCount());
    assertEquals(1.0d, meterRegistry.get("ws_push_dropped_total").counter().count());
  }

  @Test
  void should_degrade_push_interval_when_delay_exceeds_threshold() throws Exception {
    MarketWebSocketSessionRegistry sessionRegistry =
      new MarketWebSocketSessionRegistry(meterRegistry, 10);
    MarketWebSocketHandler handler =
        new MarketWebSocketHandler(
        messagingTemplate,
        new ObjectMapper(),
        sessionRegistry,
        meterRegistry,
        10,
        65536,
        1,
        10,
        1);

    handler.pushQuote("sh600519", Map.of("p", 1));
    Thread.sleep(2);
    handler.drainQueue();

    assertTrue(handler.isDegradedMode());
    ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
    verify(messagingTemplate)
      .convertAndSend(org.mockito.ArgumentMatchers.eq("/topic/market/quote/sh600519"), payloadCaptor.capture());
    Object captured = payloadCaptor.getValue();
    assertTrue(captured instanceof Map<?, ?>);
    Map<?, ?> map = (Map<?, ?>) captured;
    assertEquals(1, map.get("p"));
    assertTrue(map.containsKey("wsPushTsMillis"));
  }

  @Test
  void should_pause_push_when_payload_exceeds_buffer_limit() {
    MarketWebSocketSessionRegistry sessionRegistry =
      new MarketWebSocketSessionRegistry(meterRegistry, 10);
    MarketWebSocketHandler handler =
        new MarketWebSocketHandler(
        messagingTemplate,
        new ObjectMapper(),
        sessionRegistry,
        meterRegistry,
        10,
        64,
        1,
        10,
        5000);

    handler.pushQuote("sh600519", "x".repeat(1024));
    handler.drainQueue();

    assertEquals(1.0d, meterRegistry.get("ws_push_dropped_total").counter().count());
    verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
  }
}
