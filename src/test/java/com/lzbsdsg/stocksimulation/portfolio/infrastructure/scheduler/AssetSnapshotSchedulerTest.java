package com.lzbsdsg.stocksimulation.portfolio.infrastructure.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lzbsdsg.stocksimulation.portfolio.domain.service.AssetSnapshotService;
import com.lzbsdsg.stocksimulation.user.domain.repository.AccountRepository;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AssetSnapshotSchedulerTest {

  @Mock private AssetSnapshotService assetSnapshotService;
  @Mock private AccountRepository accountRepository;
  @Mock private StringRedisTemplate stringRedisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  private AssetSnapshotScheduler scheduler;

  @BeforeEach
  void setUp() {
    scheduler = new AssetSnapshotScheduler(assetSnapshotService, accountRepository, stringRedisTemplate);
    ReflectionTestUtils.setField(scheduler, "batchSize", 2);
    ReflectionTestUtils.setField(scheduler, "lockTtlMinutes", 30L);
    when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
  }

  @Test
  void should_take_snapshot_in_batches_when_lock_acquired() {
    when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
    when(accountRepository.findUserIdsAfter(0L, 2)).thenReturn(List.of(1001L, 1002L));
    when(accountRepository.findUserIdsAfter(1002L, 2)).thenReturn(List.of(1003L));
    when(assetSnapshotService.createDailySnapshots(any(), eq(List.of(1001L, 1002L))))
        .thenReturn(new AssetSnapshotService.SnapshotBatchResult(2, 0, 0));
    when(assetSnapshotService.createDailySnapshots(any(), eq(List.of(1003L))))
        .thenReturn(new AssetSnapshotService.SnapshotBatchResult(1, 0, 0));

    scheduler.takeSnapshot();

    verify(assetSnapshotService, times(2)).createDailySnapshots(any(), any());
  }

  @Test
  void should_skip_snapshot_when_lock_not_acquired() {
    when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(false);

    scheduler.takeSnapshot();

    verify(accountRepository, never()).findUserIdsAfter(anyLong(), anyInt());
    verify(assetSnapshotService, never()).createDailySnapshots(any(), any());
  }

  @Test
  void should_unfreeze_positions_when_lock_acquired() {
    when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
    when(assetSnapshotService.unfreezeDuePositions(any())).thenReturn(3);

    scheduler.unfreezePositions();

    verify(assetSnapshotService).unfreezeDuePositions(any());
  }
}
