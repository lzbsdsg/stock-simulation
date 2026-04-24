package com.lzbsdsg.stocksimulation.market.infrastructure.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
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

  private static final String WS_QUEUE_DELAY_TIMER_METRIC = "market.ws.queue.delay";

  private final SimpMessagingTemplate messagingTemplate;
  private final ObjectMapper objectMapper;
  private final MarketWebSocketSessionRegistry sessionRegistry;
  private final String quoteDestinationPrefix;
  private final int queueDepthLimit;
  private final int payloadBytesLimit;
  private final int normalPushIntervalMs;
  private final int degradedPushIntervalMs;
  private final int degradeLagThresholdMs;
  private final int normalDrainBatchSize;
  private final int degradedDrainBatchSize;
  private final boolean payloadSizeCheckEnabled;
  private final boolean attachPushTimestamp;

  // Maintain insertion order and coalesce by stock code to keep only the latest pending quote per symbol.
  private final LinkedHashMap<String, PushTask> pushQueue = new LinkedHashMap<>();
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
      @Value("${market.websocket.quote-destination-prefix:/topic/market/quote/}")
          String quoteDestinationPrefix,
      @Value("${market.websocket.backpressure-queue-depth:100}") int queueDepthLimit,
      @Value("${market.websocket.payload-bytes-limit:65536}") int payloadBytesLimit,
      @Value("${market.websocket.push-interval-ms:3000}") int normalPushIntervalMs,
      @Value("${market.websocket.degraded-push-interval-ms:10000}") int degradedPushIntervalMs,
      @Value("${market.websocket.degrade-lag-threshold-ms:5000}") int degradeLagThresholdMs,
      @Value("${market.websocket.drain-batch-size:32}") int normalDrainBatchSize,
      @Value("${market.websocket.degraded-drain-batch-size:128}") int degradedDrainBatchSize,
      @Value("${market.websocket.payload-size-check-enabled:false}") boolean payloadSizeCheckEnabled,
      @Value("${market.websocket.attach-push-timestamp:false}") boolean attachPushTimestamp) {
    this.messagingTemplate = messagingTemplate;
    this.objectMapper = objectMapper;
    this.sessionRegistry = sessionRegistry;
    this.quoteDestinationPrefix =
        quoteDestinationPrefix == null || quoteDestinationPrefix.isBlank()
            ? "/topic/market/quote/"
            : quoteDestinationPrefix;
    this.queueDepthLimit = queueDepthLimit;
    this.payloadBytesLimit = payloadBytesLimit;
    this.normalPushIntervalMs = normalPushIntervalMs;
    this.degradedPushIntervalMs = degradedPushIntervalMs;
    this.degradeLagThresholdMs = degradeLagThresholdMs;
    this.normalDrainBatchSize = Math.max(1, normalDrainBatchSize);
    this.degradedDrainBatchSize = Math.max(this.normalDrainBatchSize, degradedDrainBatchSize);
    this.payloadSizeCheckEnabled = payloadSizeCheckEnabled;
    this.attachPushTimestamp = attachPushTimestamp;
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
    String normalizedCode = stockCode.trim().toLowerCase();
    PushTask newTask = new PushTask(normalizedCode, payload, System.currentTimeMillis());
    synchronized (queueLock) {
      if (pushQueue.containsKey(normalizedCode)) {
        pushQueue.put(normalizedCode, newTask);
        return;
      }

      if (pushQueue.size() >= queueDepthLimit) {
        Iterator<Map.Entry<String, PushTask>> iterator = pushQueue.entrySet().iterator();
        if (iterator.hasNext()) {
          droppedTask = iterator.next().getValue();
          iterator.remove();
        }
      }
      pushQueue.put(normalizedCode, newTask);
    }

    if (droppedTask != null) {
      wsPushDroppedCounter.increment();
      log.debug("Dropped oldest ws push task stockCode={} due to backpressure", droppedTask.stockCode());
    }
  }

  /** 处理待推送队列（由调度器按固定频率触发）。 */
  public int drainQueue() {
    List<PushTask> tasksToSend;
    long now = System.currentTimeMillis();
    synchronized (queueLock) {
      if (pushQueue.isEmpty()) {
        return 0;
      }

      Iterator<Map.Entry<String, PushTask>> iterator = pushQueue.entrySet().iterator();
      PushTask headTask = iterator.next().getValue();

      if (now - headTask.enqueuedAtMs() > degradeLagThresholdMs) {
        degradedUntilMs = now + degradedPushIntervalMs * 3L;
      }

      int interval = currentPushIntervalMs(now);
      if (now - lastPushAtMs < interval) {
        return 0;
      }

      int batchSize = currentDrainBatchSize(now);
      tasksToSend = new ArrayList<>(Math.min(batchSize, pushQueue.size()));
      tasksToSend.add(headTask);
      iterator.remove();
      while (iterator.hasNext() && tasksToSend.size() < batchSize) {
        Map.Entry<String, PushTask> entry = iterator.next();
        tasksToSend.add(entry.getValue());
        iterator.remove();
      }
      lastPushAtMs = now;
    }

    for (PushTask taskToSend : tasksToSend) {
      String destination = quoteDestinationPrefix + taskToSend.stockCode();
      Object payloadToSend = attachPushTimestamp ? enrichPayloadWithLatency(taskToSend.payload()) : taskToSend.payload();
      long queueDelayMs = Math.max(now - taskToSend.enqueuedAtMs(), 0L);
      wsQueueDelayTimer.record(queueDelayMs, java.util.concurrent.TimeUnit.MILLISECONDS);
      wsPushDurationTimer.record(() -> messagingTemplate.convertAndSend(destination, payloadToSend));
      log.debug("Pushed quote for {} to {}", taskToSend.stockCode(), destination);
    }
    return tasksToSend.size();
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

  private int currentDrainBatchSize(long nowMs) {
    return nowMs < degradedUntilMs ? degradedDrainBatchSize : normalDrainBatchSize;
  }

  private boolean payloadExceedsLimit(Object payload) {
    if (!payloadSizeCheckEnabled || payloadBytesLimit <= 0) {
      return false;
    }
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
