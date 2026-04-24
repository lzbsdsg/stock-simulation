package com.lzbsdsg.stocksimulation.config;

import com.lzbsdsg.stocksimulation.auth.infrastructure.gateway.JwtTokenProvider;
import com.lzbsdsg.stocksimulation.market.infrastructure.websocket.MarketWebSocketSessionRegistry;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/** WebSocket 握手拦截器：连接上限检查 + JWT 鉴权。 */
@Component
public class WebSocketJwtHandshakeInterceptor implements HandshakeInterceptor {

  private static final String K6_BYPASS_HEADER = "X-K6-Bypass-Key";
  private static final String K6_BYPASS_USER_ID_HEADER = "X-K6-Bypass-User-Id";

  private final JwtTokenProvider jwtTokenProvider;
  private final MarketWebSocketSessionRegistry sessionRegistry;

  @Value("${app.security.k6-bypass.enabled:false}")
  private boolean k6BypassEnabled;

  @Value("${app.security.k6-bypass.key:}")
  private String k6BypassKey;

  @Value("${app.security.k6-bypass.user-id:1}")
  private long k6BypassUserId;

  public WebSocketJwtHandshakeInterceptor(
      JwtTokenProvider jwtTokenProvider, MarketWebSocketSessionRegistry sessionRegistry) {
    this.jwtTokenProvider = jwtTokenProvider;
    this.sessionRegistry = sessionRegistry;
  }

  @Override
  public boolean beforeHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      java.util.Map<String, Object> attributes) {
    if (!sessionRegistry.hasCapacity()) {
      response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
      return false;
    }

    String bypassKey = request.getHeaders().getFirst(K6_BYPASS_HEADER);
    if (k6BypassEnabled
        && k6BypassKey != null
        && !k6BypassKey.isBlank()
        && k6BypassKey.equals(bypassKey)) {
      attributes.put("wsUserId", resolveBypassUserId(request));
      return true;
    }

    String token = extractBearerToken(request);
    if (token == null || !jwtTokenProvider.validateToken(token)) {
      response.setStatusCode(HttpStatus.UNAUTHORIZED);
      return false;
    }

    Long userId = jwtTokenProvider.getUserIdFromToken(token);
    attributes.put("wsUserId", String.valueOf(userId));
    return true;
  }

  @Override
  public void afterHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Exception exception) {
    // no-op
  }

  private String extractBearerToken(ServerHttpRequest request) {
    String authorization = request.getHeaders().getFirst("Authorization");
    if (authorization != null && authorization.startsWith("Bearer ")) {
      return authorization.substring(7).trim();
    }

    URI uri = request.getURI();
    if (uri == null || uri.getQuery() == null || uri.getQuery().isBlank()) {
      return null;
    }

    String[] parts = uri.getQuery().split("&");
    for (String part : parts) {
      String[] pair = part.split("=", 2);
      if (pair.length == 2 && "access_token".equals(pair[0]) && !pair[1].isBlank()) {
        return pair[1].trim();
      }
    }
    return null;
  }

  private String resolveBypassUserId(ServerHttpRequest request) {
    String headerValue = request.getHeaders().getFirst(K6_BYPASS_USER_ID_HEADER);
    if (headerValue == null || headerValue.isBlank()) {
      return String.valueOf(k6BypassUserId);
    }
    try {
      return String.valueOf(Long.parseLong(headerValue.trim()));
    } catch (NumberFormatException ex) {
      return String.valueOf(k6BypassUserId);
    }
  }
}
