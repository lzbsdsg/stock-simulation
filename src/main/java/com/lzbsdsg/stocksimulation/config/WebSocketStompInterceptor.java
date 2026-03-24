package com.lzbsdsg.stocksimulation.config;

import com.lzbsdsg.stocksimulation.auth.infrastructure.gateway.JwtTokenProvider;
import com.lzbsdsg.stocksimulation.market.infrastructure.websocket.MarketWebSocketSessionRegistry;
import java.security.Principal;
import java.util.Collections;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

/** STOMP 入站拦截器：CONNECT 鉴权并维护连接注册表。 */
@Component
public class WebSocketStompInterceptor implements ChannelInterceptor {

  private final JwtTokenProvider jwtTokenProvider;
  private final MarketWebSocketSessionRegistry sessionRegistry;

  public WebSocketStompInterceptor(
      JwtTokenProvider jwtTokenProvider, MarketWebSocketSessionRegistry sessionRegistry) {
    this.jwtTokenProvider = jwtTokenProvider;
    this.sessionRegistry = sessionRegistry;
  }

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    if (accessor == null || accessor.getCommand() == null) {
      return message;
    }

    if (StompCommand.CONNECT.equals(accessor.getCommand())) {
      String sessionId = accessor.getSessionId();
      String userId = resolveUserId(accessor);
      if (userId == null || userId.isBlank()) {
        throw new MessageDeliveryException("Unauthorized websocket connect");
      }

      if (!sessionRegistry.tryRegisterSession(userId, sessionId)) {
        throw new MessageDeliveryException("WebSocket connection limit exceeded");
      }

      Principal principal =
          new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
      accessor.setUser(principal);
      return message;
    }

    if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
      sessionRegistry.unregisterSession(accessor.getSessionId());
    }

    return message;
  }

  private String resolveUserId(StompHeaderAccessor accessor) {
    Object fromHandshake =
        accessor.getSessionAttributes() == null ? null : accessor.getSessionAttributes().get("wsUserId");
    if (fromHandshake != null) {
      return String.valueOf(fromHandshake);
    }

    String authorization = accessor.getFirstNativeHeader("Authorization");
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      return null;
    }

    String token = authorization.substring(7).trim();
    if (!jwtTokenProvider.validateToken(token)) {
      return null;
    }
    return String.valueOf(jwtTokenProvider.getUserIdFromToken(token));
  }
}
