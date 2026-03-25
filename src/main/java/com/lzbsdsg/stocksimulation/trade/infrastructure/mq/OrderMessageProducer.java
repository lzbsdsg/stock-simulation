package com.lzbsdsg.stocksimulation.trade.infrastructure.mq;

import com.lzbsdsg.stocksimulation.config.RabbitMQConfig;
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

  /** 发送撮合消息 */
  public void sendMatchMessage(Long orderId) {
    log.info("Sending match message for orderId={}", orderId);
    rabbitTemplate.convertAndSend(
        RabbitMQConfig.TRADE_EXCHANGE, RabbitMQConfig.MATCH_ROUTING_KEY, orderId);
  }

  /** 发送交易通知消息 */
  public void sendNotification(Object notificationPayload) {
    log.info("Sending trade notification");
    rabbitTemplate.convertAndSend(
        RabbitMQConfig.TRADE_EXCHANGE,
        RabbitMQConfig.NOTIFICATION_ROUTING_KEY,
        notificationPayload);
  }

  /** 发送成交事件（fanout）。 */
  public void sendTradeFilledEvent(TradeFilledEvent event) {
    log.info("Sending trade filled event, orderId={}, tradeId={}", event.orderId(), event.tradeId());
    rabbitTemplate.convertAndSend(RabbitMQConfig.TRADE_FILLED_EXCHANGE, "", event);
  }
}
