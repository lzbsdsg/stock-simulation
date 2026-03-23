package com.lzbsdsg.stocksimulation.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/** WebSocket 配置 (STOMP over SockJS) */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

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
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws/market").setAllowedOriginPatterns("*").withSockJS();
  }

  // TODO: 配置 WebSocket 握手认证（JWT校验）
}
