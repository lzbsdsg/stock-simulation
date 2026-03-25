package com.lzbsdsg.stocksimulation.portfolio.infrastructure.scheduler;

import com.lzbsdsg.stocksimulation.portfolio.domain.service.AssetSnapshotService;
import com.lzbsdsg.stocksimulation.user.domain.repository.AccountRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 资产快照调度器
 *
 * <p>每个交易日收盘后（15:15）为所有用户拍摄资产快照。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssetSnapshotScheduler {

  private static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");
  private static final String SNAPSHOT_LOCK_PREFIX = "snapshot:daily:";
  private static final String UNFREEZE_LOCK_PREFIX = "snapshot:unfreeze:";

  private final AssetSnapshotService assetSnapshotService;
  private final AccountRepository accountRepository;
  private final StringRedisTemplate stringRedisTemplate;

  @Value("${portfolio.snapshot.batch-size:500}")
  private int batchSize;

  @Value("${portfolio.snapshot.lock-ttl-minutes:30}")
  private long lockTtlMinutes;

  /** 每个工作日 15:15 执行资产快照 */
  @Scheduled(cron = "${portfolio.snapshot.daily-cron:0 15 15 * * MON-FRI}")
  public void takeSnapshot() {
    LocalDate today = LocalDate.now(ZONE_SHANGHAI);
    String lockKey = SNAPSHOT_LOCK_PREFIX + today;
    if (!tryAcquireLock(lockKey, Duration.ofMinutes(lockTtlMinutes))) {
      log.info("portfolio.snapshot.skip_duplicate date={}", today);
      return;
    }

    int safeBatchSize = batchSize <= 0 ? 500 : batchSize;
    long lastUserId = 0L;
    int batchNo = 0;
    int totalUsers = 0;
    int totalCreated = 0;
    int totalSkipped = 0;
    int totalFailed = 0;

    while (true) {
      var userIds = accountRepository.findUserIdsAfter(lastUserId, safeBatchSize);
      if (userIds.isEmpty()) {
        break;
      }
      batchNo++;
      lastUserId = userIds.get(userIds.size() - 1);
      totalUsers += userIds.size();

      AssetSnapshotService.SnapshotBatchResult batchResult =
          assetSnapshotService.createDailySnapshots(today, userIds);
      totalCreated += batchResult.created();
      totalSkipped += batchResult.skipped();
      totalFailed += batchResult.failed();

      log.info(
          "portfolio.snapshot.batch_done date={} batchNo={} size={} created={} skipped={} failed={} lastUserId={}",
          today,
          batchNo,
          userIds.size(),
          batchResult.created(),
          batchResult.skipped(),
          batchResult.failed(),
          lastUserId);

      if (userIds.size() < safeBatchSize) {
        break;
      }
    }

    log.info(
        "portfolio.snapshot.done date={} totalUsers={} totalCreated={} totalSkipped={} totalFailed={} batchSize={}",
        today,
        totalUsers,
        totalCreated,
        totalSkipped,
        totalFailed,
        safeBatchSize);
  }

  /** 每个工作日 9:25 执行 T+1 解冻 */
  @Scheduled(cron = "${portfolio.snapshot.unfreeze-cron:0 25 9 * * MON-FRI}")
  public void unfreezePositions() {
    LocalDate today = LocalDate.now(ZONE_SHANGHAI);
    String lockKey = UNFREEZE_LOCK_PREFIX + today;
    if (!tryAcquireLock(lockKey, Duration.ofMinutes(10))) {
      log.info("portfolio.unfreeze.skip_duplicate date={}", today);
      return;
    }
    int updated = assetSnapshotService.unfreezeDuePositions(today);
    log.info("portfolio.unfreeze.done date={} affectedPositions={}", today, updated);
  }

  private boolean tryAcquireLock(String lockKey, Duration ttl) {
    Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", ttl);
    return Boolean.TRUE.equals(locked);
  }
}
