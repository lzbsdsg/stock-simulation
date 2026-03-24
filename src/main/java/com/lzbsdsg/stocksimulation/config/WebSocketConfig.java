package com.lzbsdsg.stocksimulation.config;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/** WebSocket 配置 (STOMP over SockJS) */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  private final WebSocketJwtHandshakeInterceptor webSocketJwtHandshakeInterceptor;
  private final WebSocketStompInterceptor webSocketStompInterceptor;

  @Override
  public void configureMessageBroker(MessageBrokerRegistry config) {
    // 客户端订阅的目的地前缀
    config.enableSimpleBroker("/topic", "/queue");
    // 客户端发送消息的目的地前缀
    config.setApplicationDestinationPrefixes("/app");
    // 用户私有消息前缀
    config.setUserDestinationPrefix("/user");
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(webSocketStompInterceptor);
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry
        .addEndpoint("/ws/market")
        .addInterceptors(webSocketJwtHandshakeInterceptor)
        .setAllowedOriginPatterns("*")
        .withSockJS();
  }
}
