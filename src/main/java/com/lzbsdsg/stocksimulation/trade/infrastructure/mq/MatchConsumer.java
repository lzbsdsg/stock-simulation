package com.lzbsdsg.stocksimulation.trade.infrastructure.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 撮合消费者
 *
 * <p>从 MQ 消费撮合消息，调用 MatchEngine 进行撮合处理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchConsumer {

  // TODO: 注入 TradeApplicationService 或 MatchEngine + 相关仓储

  @RabbitListener(queues = "trade.match.queue")
  public void onMatchMessage(Long orderId) {
    log.info("Received match message for orderId={}", orderId);
    // TODO: 实现撮合逻辑
    // 1. 查询订单
    // 2. 获取最新行情
    // 3. 调用 MatchEngine.tryMatch()
    // 4. 成交 → 更新 Order + Position + Account + 插入 Trade（同一事务）
    // 5. 发送成交通知
  }
}
