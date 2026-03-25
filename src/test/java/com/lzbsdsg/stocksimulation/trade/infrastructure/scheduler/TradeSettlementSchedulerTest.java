package com.lzbsdsg.stocksimulation.trade.infrastructure.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lzbsdsg.stocksimulation.trade.application.TradeApplicationService;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TradeSettlementSchedulerTest {

  @Mock private TradeApplicationService tradeApplicationService;
  @Mock private StringRedisTemplate stringRedisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  private TradeSettlementScheduler scheduler;

  @BeforeEach
  void setUp() {
    scheduler = new TradeSettlementScheduler(tradeApplicationService, stringRedisTemplate);
    ReflectionTestUtils.setField(scheduler, "archiveBatchSize", 500);
    ReflectionTestUtils.setField(scheduler, "archiveRetainDays", 7);
    when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
  }

  @Test
  void should_archive_orders_in_batches_until_last_batch_not_full() {
    when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
    when(tradeApplicationService.archiveClosedOrders(7, 500)).thenReturn(500, 120);

    scheduler.archiveOrders();

    verify(tradeApplicationService, times(2)).archiveClosedOrders(7, 500);
  }

  @Test
  void should_skip_archive_when_distributed_lock_not_acquired() {
    when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(false);

    scheduler.archiveOrders();

    verify(tradeApplicationService, never()).archiveClosedOrders(anyInt(), anyInt());
  }
}
