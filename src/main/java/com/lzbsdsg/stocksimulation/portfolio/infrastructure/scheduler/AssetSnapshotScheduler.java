package com.lzbsdsg.stocksimulation.portfolio.infrastructure.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 资产快照调度器
 *
 * <p>每个交易日收盘后（15:30）为所有用户拍摄资产快照。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssetSnapshotScheduler {

  // TODO: 注入 AssetSnapshotService

  /** 每个工作日 15:30 执行资产快照 */
  @Scheduled(cron = "0 30 15 * * MON-FRI")
  public void takeSnapshot() {
    log.info("Starting daily asset snapshot...");
    // TODO: 遍历所有活跃用户，创建资产快照
    log.info("Daily asset snapshot completed");
  }

  /** 每个工作日 9:25 执行 T+1 解冻 */
  @Scheduled(cron = "0 25 9 * * MON-FRI")
  public void unfreezePositions() {
    log.info("Starting T+1 position unfreeze...");
    // TODO: 查询所有 frozenUntil <= today 的持仓，执行解冻
    log.info("T+1 position unfreeze completed");
  }
}
