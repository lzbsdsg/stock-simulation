package com.lzbsdsg.stocksimulation.market.infrastructure.websocket;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** WebSocket 会话注册表，负责连接计数、用户会话映射与上限控制。 */
@Slf4j
@Component
public class MarketWebSocketSessionRegistry {

  private final int maxConnections;
  private final AtomicInteger activeConnections = new AtomicInteger(0);
  private final ConcurrentMap<String, Set<String>> userSessions = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, String> sessionUsers = new ConcurrentHashMap<>();

  public MarketWebSocketSessionRegistry(
      MeterRegistry meterRegistry,
      @Value("${market.websocket.max-connections:10000}") int maxConnections) {
    this.maxConnections = maxConnections;
    Gauge.builder("ws_active_connections", activeConnections, AtomicInteger::get).register(meterRegistry);
  }

  public boolean hasCapacity() {
    return activeConnections.get() < maxConnections;
  }

  public boolean tryRegisterSession(String userId, String sessionId) {
    if (userId == null || userId.isBlank() || sessionId == null || sessionId.isBlank()) {
      return false;
    }

    int newCount = activeConnections.incrementAndGet();
    if (newCount > maxConnections) {
      activeConnections.decrementAndGet();
      return false;
    }

    sessionUsers.put(sessionId, userId);
    userSessions.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet()).add(sessionId);

    if (newCount > 8000) {
      log.warn("ws.connection.high activeConnections={}", newCount);
    }
    return true;
  }

  public void unregisterSession(String sessionId) {
    if (sessionId == null || sessionId.isBlank()) {
      return;
    }

    String userId = sessionUsers.remove(sessionId);
    if (userId == null) {
      return;
    }

    Set<String> sessions = userSessions.get(userId);
    if (sessions != null) {
      sessions.remove(sessionId);
      if (sessions.isEmpty()) {
        userSessions.remove(userId, sessions);
      }
    }

    activeConnections.updateAndGet(current -> Math.max(0, current - 1));
  }

  public int getActiveConnectionCount() {
    return activeConnections.get();
  }

  public Map<String, Set<String>> snapshotUserSessions() {
    return Map.copyOf(userSessions);
  }
}
