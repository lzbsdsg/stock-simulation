package com.lzbsdsg.stocksimulation.market.infrastructure.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * 行情 WebSocket 推送处理器
 *
 * <p>通过 STOMP 将实时行情推送给前端订阅者。 频道示例: /topic/market/quote/sh600519
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketWebSocketHandler {

  private final SimpMessagingTemplate messagingTemplate;

  private static final String QUOTE_TOPIC_PREFIX = "/topic/market/quote/";

  /** 推送单只股票行情到订阅者 */
  public void pushQuote(String stockCode, Object quoteSnapshot) {
    String destination = QUOTE_TOPIC_PREFIX + stockCode;
    messagingTemplate.convertAndSend(destination, quoteSnapshot);
    log.debug("Pushed quote for {} to {}", stockCode, destination);
  }

  /** 推送消息给指定用户 */
  public void pushToUser(String userId, String destination, Object payload) {
    messagingTemplate.convertAndSendToUser(userId, destination, payload);
  }
}
