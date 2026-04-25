package com.lzbsdsg.stocksimulation.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.config.StompBrokerRelayRegistration;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

/** WebSocket 配置 (STOMP over SockJS) */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  private final WebSocketJwtHandshakeInterceptor webSocketJwtHandshakeInterceptor;
  private final WebSocketStompInterceptor webSocketStompInterceptor;

  @Value("${market.websocket.broker.inbound-core-pool-size:4}")
  private int inboundCorePoolSize;

  @Value("${market.websocket.broker.inbound-max-pool-size:16}")
  private int inboundMaxPoolSize;

  @Value("${market.websocket.broker.outbound-core-pool-size:8}")
  private int outboundCorePoolSize;

  @Value("${market.websocket.broker.outbound-max-pool-size:32}")
  private int outboundMaxPoolSize;

  @Value("${market.websocket.broker.queue-capacity:5000}")
  private int brokerQueueCapacity;

  @Value("${market.websocket.broker.relay-enabled:false}")
  private boolean brokerRelayEnabled;

  @Value("${market.websocket.broker.relay-host:localhost}")
  private String brokerRelayHost;

  @Value("${market.websocket.broker.relay-port:61613}")
  private int brokerRelayPort;

  @Value("${market.websocket.broker.client-login:guest}")
  private String brokerClientLogin;

  @Value("${market.websocket.broker.client-passcode:guest}")
  private String brokerClientPasscode;

  @Value("${market.websocket.broker.system-login:guest}")
  private String brokerSystemLogin;

  @Value("${market.websocket.broker.system-passcode:guest}")
  private String brokerSystemPasscode;

  @Value("${market.websocket.broker.virtual-host:/}")
  private String brokerVirtualHost;

  @Value("${market.websocket.transport.message-size-limit:65536}")
  private int transportMessageSizeLimit;

  @Value("${market.websocket.transport.send-buffer-size-limit:524288}")
  private int transportSendBufferSizeLimit;

  @Value("${market.websocket.transport.send-time-limit-ms:15000}")
  private int transportSendTimeLimitMs;

  @Override
  public void configureMessageBroker(MessageBrokerRegistry config) {
    if (brokerRelayEnabled) {
      StompBrokerRelayRegistration relay = config.enableStompBrokerRelay("/topic", "/queue");
      relay.setRelayHost(brokerRelayHost);
      relay.setRelayPort(brokerRelayPort);
      relay.setClientLogin(brokerClientLogin);
      relay.setClientPasscode(brokerClientPasscode);
      relay.setSystemLogin(brokerSystemLogin);
      relay.setSystemPasscode(brokerSystemPasscode);
      relay.setVirtualHost(brokerVirtualHost);
      relay.setSystemHeartbeatSendInterval(10000);
      relay.setSystemHeartbeatReceiveInterval(10000);
    } else {
      config.enableSimpleBroker("/topic", "/queue");
    }
    // 客户端发送消息的目的地前缀
    config.setApplicationDestinationPrefixes("/app");
    // 用户私有消息前缀
    config.setUserDestinationPrefix("/user");
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration
        .taskExecutor()
        .corePoolSize(Math.max(1, inboundCorePoolSize))
        .maxPoolSize(Math.max(inboundCorePoolSize, inboundMaxPoolSize))
        .queueCapacity(Math.max(100, brokerQueueCapacity));
    registration.interceptors(webSocketStompInterceptor);
  }

  @Override
  public void configureClientOutboundChannel(ChannelRegistration registration) {
    registration
        .taskExecutor()
        .corePoolSize(Math.max(1, outboundCorePoolSize))
        .maxPoolSize(Math.max(outboundCorePoolSize, outboundMaxPoolSize))
        .queueCapacity(Math.max(100, brokerQueueCapacity));
  }

  @Override
  public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
    registry.setMessageSizeLimit(Math.max(1024, transportMessageSizeLimit));
    registry.setSendBufferSizeLimit(Math.max(4096, transportSendBufferSizeLimit));
    registry.setSendTimeLimit(Math.max(1000, transportSendTimeLimitMs));
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry
        .addEndpoint("/ws/market-native")
        .addInterceptors(webSocketJwtHandshakeInterceptor)
        .setAllowedOriginPatterns("*");
    registry
        .addEndpoint("/ws/market")
        .addInterceptors(webSocketJwtHandshakeInterceptor)
        .setAllowedOriginPatterns("*")
        .withSockJS();
  }
}
