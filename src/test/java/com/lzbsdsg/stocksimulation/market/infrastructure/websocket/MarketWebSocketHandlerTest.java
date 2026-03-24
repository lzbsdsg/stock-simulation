package com.lzbsdsg.stocksimulation.market.infrastructure.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class MarketWebSocketHandlerTest {

  private SimpMessagingTemplate messagingTemplate;
  private SimpleMeterRegistry meterRegistry;

  @BeforeEach
  void setUp() {
    messagingTemplate = Mockito.mock(SimpMessagingTemplate.class);
    meterRegistry = new SimpleMeterRegistry();
  }

  @Test
  void should_register_and_unregister_session() {
    MarketWebSocketSessionRegistry sessionRegistry =
      new MarketWebSocketSessionRegistry(meterRegistry, 2);
    MarketWebSocketHandler handler =
        new MarketWebSocketHandler(
        messagingTemplate,
        new ObjectMapper(),
        sessionRegistry,
        meterRegistry,
        100,
        65536,
        1,
        10,
        5);

    assertTrue(handler.tryRegisterSession("1001", "s1"));
    assertEquals(1, handler.getActiveConnectionCount());

    handler.unregisterSession("s1");
    assertEquals(0, handler.getActiveConnectionCount());
    assertTrue(handler.snapshotUserSessions().isEmpty());
  }

  @Test
  void should_reject_when_connection_limit_exceeded() {
    MarketWebSocketSessionRegistry sessionRegistry =
      new MarketWebSocketSessionRegistry(meterRegistry, 1);
    MarketWebSocketHandler handler =
        new MarketWebSocketHandler(
        messagingTemplate,
        new ObjectMapper(),
        sessionRegistry,
        meterRegistry,
        100,
        65536,
        1,
        10,
        5);

    assertTrue(handler.tryRegisterSession("1001", "s1"));
    assertFalse(handler.tryRegisterSession("1002", "s2"));
    assertEquals(1, handler.getActiveConnectionCount());
  }

  @Test
  void should_drop_oldest_when_queue_is_full() {
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

    handler.pushQuote("sh600519", Map.of("price", 1));
    handler.pushQuote("sz000001", Map.of("price", 2));

    assertEquals(1, handler.getQueuedTaskCount());
    assertEquals(1.0d, meterRegistry.get("ws_push_dropped_total").counter().count());

    handler.drainQueue();
    ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
    verify(messagingTemplate, times(1))
      .convertAndSend(org.mockito.ArgumentMatchers.eq("/topic/market/quote/sz000001"), payloadCaptor.capture());

    Object captured = payloadCaptor.getValue();
    assertTrue(captured instanceof Map<?, ?>);
    Map<?, ?> map = (Map<?, ?>) captured;
    assertEquals(2, map.get("price"));
    assertTrue(map.containsKey("wsPushTsMillis"));
  }

  @Test
  void should_enter_degraded_mode_when_lag_is_high() throws Exception {
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
        1000,
        5);

    handler.pushQuote("sh600519", Map.of("price", 1));
    Thread.sleep(20);
    handler.drainQueue();

    assertTrue(handler.isDegradedMode());
  }
}
