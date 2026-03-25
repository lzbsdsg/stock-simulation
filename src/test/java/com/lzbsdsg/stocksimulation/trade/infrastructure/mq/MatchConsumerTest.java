package com.lzbsdsg.stocksimulation.trade.infrastructure.mq;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lzbsdsg.stocksimulation.common.exception.BizException;
import com.lzbsdsg.stocksimulation.common.result.ErrorCode;
import com.lzbsdsg.stocksimulation.trade.application.TradeApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

@ExtendWith(MockitoExtension.class)
class MatchConsumerTest {

  @Mock private TradeApplicationService tradeApplicationService;

  private MatchConsumer matchConsumer;

  @BeforeEach
  void setUp() {
    matchConsumer = new MatchConsumer(tradeApplicationService);
  }

  @Test
  void should_match_success_without_retry() {
    when(tradeApplicationService.matchOrder(1001L)).thenReturn(TradeApplicationService.MatchResult.MATCHED);

    matchConsumer.onMatchMessage(1001L);

    verify(tradeApplicationService, times(1)).matchOrder(1001L);
  }

  @Test
  void should_retry_when_optimistic_lock_conflict_then_success() {
    when(tradeApplicationService.matchOrder(1002L))
        .thenThrow(new BizException(ErrorCode.TRADE_OPTIMISTIC_LOCK_CONFLICT))
        .thenReturn(TradeApplicationService.MatchResult.MATCHED);

    matchConsumer.onMatchMessage(1002L);

    verify(tradeApplicationService, times(2)).matchOrder(1002L);
  }

  @Test
  void should_send_to_dlq_after_max_retry_exhausted() {
    when(tradeApplicationService.matchOrder(1003L))
        .thenThrow(new BizException(ErrorCode.TRADE_OPTIMISTIC_LOCK_CONFLICT));

    assertThrows(AmqpRejectAndDontRequeueException.class, () -> matchConsumer.onMatchMessage(1003L));
    verify(tradeApplicationService, times(3)).matchOrder(1003L);
  }

  @Test
  void should_not_retry_when_business_error_is_not_optimistic_conflict() {
    when(tradeApplicationService.matchOrder(1004L)).thenThrow(new BizException(ErrorCode.MARKET_DATA_UNAVAILABLE));

    assertThrows(BizException.class, () -> matchConsumer.onMatchMessage(1004L));
    verify(tradeApplicationService, times(1)).matchOrder(1004L);
  }

  @Test
  void should_skip_idempotent_order_as_normal() {
    when(tradeApplicationService.matchOrder(1005L))
        .thenReturn(TradeApplicationService.MatchResult.SKIPPED_ALREADY_DONE);

    matchConsumer.onMatchMessage(1005L);

    verify(tradeApplicationService, times(1)).matchOrder(1005L);
  }
}
