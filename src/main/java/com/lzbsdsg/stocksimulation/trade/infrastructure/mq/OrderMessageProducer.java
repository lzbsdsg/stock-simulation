package com.lzbsdsg.stocksimulation.trade.infrastructure.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 订单消息生产者
 *
 * <p>下单成功后发送撮合消息到 RabbitMQ。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMessageProducer {

  private final RabbitTemplate rabbitTemplate;

  private static final String EXCHANGE = "trade.exchange";
  private static final String MATCH_ROUTING_KEY = "trade.match";
  private static final String NOTIFICATION_ROUTING_KEY = "trade.notification";

  /** 发送撮合消息 */
  public void sendMatchMessage(Long orderId) {
    log.info("Sending match message for orderId={}", orderId);
    rabbitTemplate.convertAndSend(EXCHANGE, MATCH_ROUTING_KEY, orderId);
  }

  /** 发送交易通知消息 */
  public void sendNotification(Object notificationPayload) {
    log.info("Sending trade notification");
    rabbitTemplate.convertAndSend(EXCHANGE, NOTIFICATION_ROUTING_KEY, notificationPayload);
  }
}
