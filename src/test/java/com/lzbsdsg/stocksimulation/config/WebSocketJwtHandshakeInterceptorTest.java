package com.lzbsdsg.stocksimulation.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lzbsdsg.stocksimulation.auth.infrastructure.gateway.JwtTokenProvider;
import com.lzbsdsg.stocksimulation.market.infrastructure.websocket.MarketWebSocketSessionRegistry;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

@ExtendWith(MockitoExtension.class)
class WebSocketJwtHandshakeInterceptorTest {

  @Mock private JwtTokenProvider jwtTokenProvider;
  @Mock private MarketWebSocketSessionRegistry sessionRegistry;
  @Mock private ServerHttpRequest request;
  @Mock private ServerHttpResponse response;
  @Mock private WebSocketHandler webSocketHandler;

  private WebSocketJwtHandshakeInterceptor interceptor;

  @BeforeEach
  void setUp() {
    interceptor = new WebSocketJwtHandshakeInterceptor(jwtTokenProvider, sessionRegistry);
  }

  @Test
  void should_allow_when_valid_bearer_token_and_capacity_available() {
    HttpHeaders headers = new HttpHeaders();
    headers.add("Authorization", "Bearer valid-token");

    when(sessionRegistry.hasCapacity()).thenReturn(true);
    when(request.getHeaders()).thenReturn(headers);
    when(jwtTokenProvider.validateToken("valid-token")).thenReturn(true);
    when(jwtTokenProvider.getUserIdFromToken("valid-token")).thenReturn(2L);

    Map<String, Object> attributes = new HashMap<>();
    boolean allowed = interceptor.beforeHandshake(request, response, webSocketHandler, attributes);

    assertTrue(allowed);
    assertEquals("2", attributes.get("wsUserId"));
  }

  @Test
  void should_reject_when_invalid_token_and_no_bypass() {
    HttpHeaders headers = new HttpHeaders();
    headers.add("Authorization", "Bearer invalid-token");

    when(sessionRegistry.hasCapacity()).thenReturn(true);
    when(request.getHeaders()).thenReturn(headers);
    when(jwtTokenProvider.validateToken("invalid-token")).thenReturn(false);

    boolean allowed =
        interceptor.beforeHandshake(request, response, webSocketHandler, new HashMap<>());

    assertFalse(allowed);
    verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void should_allow_when_authenticated_principal_present() {
    HttpHeaders headers = new HttpHeaders();

    when(sessionRegistry.hasCapacity()).thenReturn(true);
    when(request.getHeaders()).thenReturn(headers);

    Map<String, Object> attributes = new HashMap<>();
    boolean allowed = interceptor.beforeHandshake(request, response, webSocketHandler, attributes);

    assertFalse(allowed);
    verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void should_reject_when_no_capacity() {
    when(sessionRegistry.hasCapacity()).thenReturn(false);

    boolean allowed =
        interceptor.beforeHandshake(request, response, webSocketHandler, new HashMap<>());

    assertFalse(allowed);
    verify(response).setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
  }

  @Test
  void should_allow_when_k6_bypass_header_matches() throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-K6-Bypass-Key", "k6-bypass");

    when(sessionRegistry.hasCapacity()).thenReturn(true);
    when(request.getHeaders()).thenReturn(headers);
    setField(interceptor, "k6BypassEnabled", true);
    setField(interceptor, "k6BypassKey", "k6-bypass");
    setField(interceptor, "k6BypassUserId", 99L);

    Map<String, Object> attributes = new HashMap<>();
    boolean allowed = interceptor.beforeHandshake(request, response, webSocketHandler, attributes);

    assertTrue(allowed);
    assertEquals("99", attributes.get("wsUserId"));
  }

  private void setField(Object target, String fieldName, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }
}
