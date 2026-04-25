package com.lzbsdsg.stocksimulation.trade.infrastructure.mq;

import com.lzbsdsg.stocksimulation.common.exception.BizException;
import com.lzbsdsg.stocksimulation.common.result.ErrorCode;
import com.lzbsdsg.stocksimulation.config.RabbitMQConfig;
import com.lzbsdsg.stocksimulation.trade.application.TradeApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.OptimisticLockingFailureException;
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

  private static final int MAX_RETRY = 3;
  private static final long INITIAL_BACKOFF_MS = 50L;

  private final TradeApplicationService tradeApplicationService;

  @RabbitListener(
      queues = RabbitMQConfig.MATCH_QUEUE,
      containerFactory = "matchRabbitListenerContainerFactory")
  public void onMatchMessage(Long orderId) {
    for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
      try {
        TradeApplicationService.MatchResult result = tradeApplicationService.matchOrder(orderId);
        log.debug("match consumed: orderId={}, attempt={}, result={}", orderId, attempt, result);
        return;
      } catch (BizException ex) {
        if (ex.getErrorCode() == ErrorCode.TRADE_OPTIMISTIC_LOCK_CONFLICT && attempt < MAX_RETRY) {
          sleepBackoff(attempt);
          continue;
        }
        if (ex.getErrorCode() == ErrorCode.TRADE_OPTIMISTIC_LOCK_CONFLICT) {
          throw toDeadLetter(orderId, attempt, ex);
        }
        throw ex;
      } catch (OptimisticLockingFailureException ex) {
        if (attempt < MAX_RETRY) {
          sleepBackoff(attempt);
          continue;
        }
        throw toDeadLetter(orderId, attempt, ex);
      }
    }
    throw new AmqpRejectAndDontRequeueException(
        String.format("match exhausted retries, orderId=%d", orderId));
  }

  private void sleepBackoff(int attempt) {
    long backoffMs = INITIAL_BACKOFF_MS * (1L << Math.max(attempt - 1, 0));
    log.debug("match retry backoff: attempt={}, sleepMs={}", attempt, backoffMs);
    try {
      Thread.sleep(backoffMs);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new AmqpRejectAndDontRequeueException("match retry interrupted", ex);
    }
  }

  private AmqpRejectAndDontRequeueException toDeadLetter(Long orderId, int attempt, Exception ex) {
    return new AmqpRejectAndDontRequeueException(
        String.format("match failed after retries, orderId=%d, attempts=%d", orderId, attempt), ex);
  }
}
