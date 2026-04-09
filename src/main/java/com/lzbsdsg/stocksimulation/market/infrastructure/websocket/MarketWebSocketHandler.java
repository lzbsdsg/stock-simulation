package com.lzbsdsg.stocksimulation.market.infrastructure.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * 行情 WebSocket 推送处理器
 *
 * <p>
 * 通过 STOMP 将实时行情推送给前端订阅者。 频道示例: /topic/market/quote/sh600519
 */
@Slf4j
@Component
public class MarketWebSocketHandler {

  private static final String QUOTE_TOPIC_PREFIX = "/topic/market/quote/";
  private static final String WS_QUEUE_DELAY_TIMER_METRIC = "market.ws.queue.delay";

  private final SimpMessagingTemplate messagingTemplate;
  private final ObjectMapper objectMapper;
  private final MarketWebSocketSessionRegistry sessionRegistry;
  private final int queueDepthLimit;
  private final int payloadBytesLimit;
  private final int normalPushIntervalMs;
  private final int degradedPushIntervalMs;
  private final int degradeLagThresholdMs;

  private final Deque<PushTask> pushQueue = new ArrayDeque<>();
  private final Object queueLock = new Object();

  private final Counter wsPushDroppedCounter;
  private final Timer wsQueueDelayTimer;
  private final Timer wsPushDurationTimer;

  private volatile long lastPushAtMs;
  private volatile long degradedUntilMs;

  public MarketWebSocketHandler(
      SimpMessagingTemplate messagingTemplate,
      ObjectMapper objectMapper,
      MarketWebSocketSessionRegistry sessionRegistry,
      MeterRegistry meterRegistry,
      @Value("${market.websocket.backpressure-queue-depth:100}") int queueDepthLimit,
      @Value("${market.websocket.payload-bytes-limit:65536}") int payloadBytesLimit,
      @Value("${market.websocket.push-interval-ms:3000}") int normalPushIntervalMs,
      @Value("${market.websocket.degraded-push-interval-ms:10000}") int degradedPushIntervalMs,
      @Value("${market.websocket.degrade-lag-threshold-ms:5000}") int degradeLagThresholdMs) {
    this.messagingTemplate = messagingTemplate;
    this.objectMapper = objectMapper;
    this.sessionRegistry = sessionRegistry;
    this.queueDepthLimit = queueDepthLimit;
    this.payloadBytesLimit = payloadBytesLimit;
    this.normalPushIntervalMs = normalPushIntervalMs;
    this.degradedPushIntervalMs = degradedPushIntervalMs;
    this.degradeLagThresholdMs = degradeLagThresholdMs;
    this.wsPushDroppedCounter = meterRegistry.counter("ws_push_dropped_total");
    this.wsQueueDelayTimer = Timer.builder(WS_QUEUE_DELAY_TIMER_METRIC)
        .description("Queue waiting delay before websocket send")
        .register(meterRegistry);
    this.wsPushDurationTimer = meterRegistry.timer("ws_push_duration_seconds");
  }

  public boolean hasCapacity() {
    return sessionRegistry.hasCapacity();
  }

  public boolean tryRegisterSession(String userId, String sessionId) {
    return sessionRegistry.tryRegisterSession(userId, sessionId);
  }

  public void unregisterSession(String sessionId) {
    sessionRegistry.unregisterSession(sessionId);
  }

  /** 推送单只股票行情到订阅者 */
  public void pushQuote(String stockCode, Object quoteSnapshot) {
    if (stockCode == null || stockCode.isBlank() || quoteSnapshot == null) {
      return;
    }

    Object payload = quoteSnapshot;

    if (payloadExceedsLimit(payload)) {
      wsPushDroppedCounter.increment();
      log.debug("Dropped quote push due to payload limit stockCode={} limit={}B", stockCode, payloadBytesLimit);
      return;
    }

    PushTask droppedTask = null;
    synchronized (queueLock) {
      if (pushQueue.size() >= queueDepthLimit) {
        droppedTask = pushQueue.pollFirst();
      }
      pushQueue.offerLast(new PushTask(stockCode.trim().toLowerCase(), payload, System.currentTimeMillis()));
    }

    if (droppedTask != null) {
      wsPushDroppedCounter.increment();
      log.debug("Dropped oldest ws push task stockCode={} due to backpressure", droppedTask.stockCode());
    }
  }

  /** 处理待推送队列（由调度器按固定频率触发）。 */
  public void drainQueue() {
    PushTask nextTask;
    long now = System.currentTimeMillis();
    synchronized (queueLock) {
      nextTask = pushQueue.peekFirst();
      if (nextTask == null) {
        return;
      }

      if (now - nextTask.enqueuedAtMs() > degradeLagThresholdMs) {
        degradedUntilMs = now + degradedPushIntervalMs * 3L;
      }

      int interval = currentPushIntervalMs(now);
      if (now - lastPushAtMs < interval) {
        return;
      }
      nextTask = pushQueue.pollFirst();
      lastPushAtMs = now;
    }

    PushTask taskToSend = nextTask;
    String destination = QUOTE_TOPIC_PREFIX + taskToSend.stockCode();
    Object payloadToSend = enrichPayloadWithLatency(taskToSend.payload());
    long queueDelayMs = Math.max(now - taskToSend.enqueuedAtMs(), 0L);
    wsQueueDelayTimer.record(queueDelayMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    wsPushDurationTimer.record(() -> messagingTemplate.convertAndSend(destination, payloadToSend));
    log.debug("Pushed quote for {} to {}", taskToSend.stockCode(), destination);
  }

  /** 推送消息给指定用户 */
  public void pushToUser(String userId, String destination, Object payload) {
    messagingTemplate.convertAndSendToUser(userId, destination, payload);
  }

  public int getActiveConnectionCount() {
    return sessionRegistry.getActiveConnectionCount();
  }

  public int getQueuedTaskCount() {
    synchronized (queueLock) {
      return pushQueue.size();
    }
  }

  public boolean isDegradedMode() {
    return System.currentTimeMillis() < degradedUntilMs;
  }

  public double getDroppedTotal() {
    return wsPushDroppedCounter.count();
  }

  private int currentPushIntervalMs(long nowMs) {
    return nowMs < degradedUntilMs ? degradedPushIntervalMs : normalPushIntervalMs;
  }

  private boolean payloadExceedsLimit(Object payload) {
    try {
      return objectMapper.writeValueAsBytes(payload).length > payloadBytesLimit;
    } catch (JsonProcessingException ex) {
      return true;
    }
  }

  private Object enrichPayloadWithLatency(Object payload) {
    try {
      Map<String, Object> mapPayload = new LinkedHashMap<>();
      mapPayload.putAll(objectMapper.convertValue(payload, Map.class));
      mapPayload.put("wsPushTsMillis", System.currentTimeMillis());
      return mapPayload;
    } catch (IllegalArgumentException ex) {
      return payload;
    }
  }

  java.util.Map<String, java.util.Set<String>> snapshotUserSessions() {
    return sessionRegistry.snapshotUserSessions();
  }

  private record PushTask(String stockCode, Object payload, long enqueuedAtMs) {
  }
}
