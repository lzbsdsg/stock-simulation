package com.lzbsdsg.stocksimulation.market.infrastructure.scheduler;

import com.lzbsdsg.stocksimulation.market.domain.service.MarketDataFacade;
import com.lzbsdsg.stocksimulation.market.infrastructure.websocket.MarketWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

  private final MarketDataFacade marketDataFacade;
  private final MarketWebSocketHandler webSocketHandler;

  // TODO: 注入 WatchlistApplicationService 或 Redis 中订阅列表获取活跃股票代码

  /** 每 3 秒推送一次实时行情 */
  @Scheduled(fixedRate = 3000)
  public void pushRealTimeQuotes() {
    // TODO: 判断是否在交易时间段内
    // TODO: 获取所有活跃订阅的股票代码列表
    // TODO: 批量拉取行情
    // TODO: 逐只推送给 WebSocket 订阅者
    log.trace("MarketPushScheduler triggered — not yet implemented");
  }
}
