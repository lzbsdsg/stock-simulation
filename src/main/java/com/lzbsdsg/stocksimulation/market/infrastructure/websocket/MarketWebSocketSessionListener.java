package com.lzbsdsg.stocksimulation.market.infrastructure.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/** 兜底清理断连会话，避免异常断线导致注册表残留。 */
@Component
@RequiredArgsConstructor
public class MarketWebSocketSessionListener {

  private final MarketWebSocketHandler marketWebSocketHandler;

  @EventListener
  public void onDisconnect(SessionDisconnectEvent event) {
    StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
    marketWebSocketHandler.unregisterSession(accessor.getSessionId());
  }
}
