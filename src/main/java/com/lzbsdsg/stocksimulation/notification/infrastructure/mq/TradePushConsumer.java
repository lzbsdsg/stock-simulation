package com.lzbsdsg.stocksimulation.notification.infrastructure.mq;

import com.lzbsdsg.stocksimulation.config.RabbitMQConfig;
import com.lzbsdsg.stocksimulation.notification.application.NotificationApplicationService;
import com.lzbsdsg.stocksimulation.trade.infrastructure.mq.TradeFilledEvent;
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

  @RabbitListener(queues = RabbitMQConfig.NOTIFICATION_QUEUE)
  public void onTradeNotification(TradeFilledEvent event) {
    log.info(
        "Received trade filled event: orderId={}, tradeId={}, userId={}",
        event.orderId(),
        event.tradeId(),
        event.userId());
    notificationApplicationService.sendNotification(
        event.userId(),
        "成交提醒",
        String.format(
            "%s %s 成交 %d 股，成交价 %s",
            event.stockCode(), event.side(), event.tradeQuantity(), event.tradePrice()),
        "TRADE_FILLED");
  }
}
