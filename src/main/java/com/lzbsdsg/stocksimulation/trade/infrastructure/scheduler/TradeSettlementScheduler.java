package com.lzbsdsg.stocksimulation.trade.infrastructure.scheduler;

import com.lzbsdsg.stocksimulation.trade.application.TradeApplicationService;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 收盘结算调度器。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradeSettlementScheduler {

  private static final int EXPIRE_BATCH_SIZE = 200;
  private static final String CLOSE_LOCK_PREFIX = "trade:settlement:close:";
  private static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");

  private final TradeApplicationService tradeApplicationService;
  private final StringRedisTemplate stringRedisTemplate;

  /** 每个交易日 15:00 执行：过期待成交订单 + 修正当日买入持仓 T+1。 */
  @Scheduled(cron = "${trade.settlement.close-cron:0 0 15 * * MON-FRI}")
  public void closeSettlement() {
    LocalDate today = LocalDate.now(ZONE_SHANGHAI);
    String lockKey = CLOSE_LOCK_PREFIX + today;
    Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", Duration.ofMinutes(10));
    if (!Boolean.TRUE.equals(locked)) {
      log.info("trade.close.skip_duplicate date={}", today);
      return;
    }

    int totalExpired = 0;
    while (true) {
      int processed = tradeApplicationService.expirePendingOrdersAtClose(EXPIRE_BATCH_SIZE);
      totalExpired += processed;
      if (processed < EXPIRE_BATCH_SIZE) {
        break;
      }
    }
    int marked = tradeApplicationService.markTodayBuyPositionsFrozenUntil();
    log.info("trade.close.done date={} expiredOrders={} markedPositions={}", today, totalExpired, marked);
  }
}
