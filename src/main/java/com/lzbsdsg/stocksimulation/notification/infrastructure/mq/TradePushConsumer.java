package com.lzbsdsg.stocksimulation.notification.infrastructure.mq;

import com.lzbsdsg.stocksimulation.notification.application.NotificationApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 交易推送消费者
 *
 * <p>从 MQ 消费交易通知消息，创建通知并通过 WebSocket 推送给用户。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradePushConsumer {

  private final NotificationApplicationService notificationApplicationService;

  @RabbitListener(queues = "trade.notification.queue")
  public void onTradeNotification(Object message) {
    log.info("Received trade notification message: {}", message);
    // TODO: 解析消息，创建通知记录，WebSocket 推送给用户
  }
}
