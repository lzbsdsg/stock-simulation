package com.lzbsdsg.stocksimulation.market.infrastructure.scheduler;

import com.lzbsdsg.stocksimulation.market.infrastructure.websocket.MarketWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 行情定时推送调度器
 *
 * <p>定时拉取行情数据并通过 WebSocket 推送给订阅用户。 交易时间段（9:30-11:30, 13:00-15:00）每 3 秒执行一次。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketPushScheduler {

  private final MarketWebSocketHandler webSocketHandler;

  @Value("${market.websocket.dispatch-enabled:true}")
  private boolean dispatchEnabled;

  /** 定时派发行情推送任务，具体节流/背压策略由 MarketWebSocketHandler 统一处理。 */
  @Scheduled(fixedRateString = "${market.websocket.dispatch-interval-ms:500}")
  public void dispatchQueuedQuotes() {
    if (!dispatchEnabled) {
      return;
    }
    int dispatched = webSocketHandler.drainQueue();
    if (dispatched > 0) {
      log.trace("MarketPushScheduler dispatched queued quotes count={}", dispatched);
    }
  }
}
