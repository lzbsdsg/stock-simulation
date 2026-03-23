package com.lzbsdsg.stocksimulation.market.domain.service;

import com.lzbsdsg.stocksimulation.market.domain.entity.KLinePeriod;
import com.lzbsdsg.stocksimulation.market.domain.entity.KLinePoint;
import com.lzbsdsg.stocksimulation.market.domain.entity.QuoteSnapshot;
import com.lzbsdsg.stocksimulation.market.domain.gateway.MarketDataProvider;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 行情数据门面（领域服务）
 *
 * <p>负责协调 Provider + 缓存 + 降级策略
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataFacade {

  private final List<MarketDataProvider> providers;

  // TODO: 注入 MarketCacheGateway

  /** 获取单只股票行情（先缓存 → Provider → 降级） */
  public QuoteSnapshot getQuote(String stockCode) {
    // TODO: 1. 查 Redis 缓存
    // TODO: 2. 缓存未命中，遍历 providers（按优先级），调用 getQuote
    // TODO: 3. 写入缓存 TTL=5s
    // TODO: 4. 所有 Provider 失败 → 返回最近一次缓存（降级）或抛异常
    for (MarketDataProvider provider : providers) {
      if (provider.isAvailable()) {
        try {
          return provider.getQuote(stockCode);
        } catch (Exception e) {
          log.warn(
              "Provider {} getQuote failed for {}: {}",
              provider.getClass().getSimpleName(),
              stockCode,
              e.getMessage());
        }
      }
    }
    throw new com.lzbsdsg.stocksimulation.common.exception.BizException(
        com.lzbsdsg.stocksimulation.common.result.ErrorCode.MARKET_DATA_UNAVAILABLE);
  }

  /** 批量获取行情 */
  public List<QuoteSnapshot> batchGetQuotes(List<String> stockCodes) {
    // TODO: 批量缓存查询 + 降级
    for (MarketDataProvider provider : providers) {
      if (provider.isAvailable()) {
        try {
          return provider.batchGetQuotes(stockCodes);
        } catch (Exception e) {
          log.warn(
              "Provider {} batchGetQuotes failed: {}",
              provider.getClass().getSimpleName(),
              e.getMessage());
        }
      }
    }
    throw new com.lzbsdsg.stocksimulation.common.exception.BizException(
        com.lzbsdsg.stocksimulation.common.result.ErrorCode.MARKET_DATA_UNAVAILABLE);
  }

  /** 获取K线 */
  public List<KLinePoint> getKLine(
      String stockCode, KLinePeriod period, LocalDate from, LocalDate to) {
    // TODO: K线缓存 TTL=60s
    for (MarketDataProvider provider : providers) {
      if (provider.isAvailable()) {
        try {
          return provider.getKLine(stockCode, period, from, to);
        } catch (Exception e) {
          log.warn(
              "Provider {} getKLine failed for {}: {}",
              provider.getClass().getSimpleName(),
              stockCode,
              e.getMessage());
        }
      }
    }
    throw new com.lzbsdsg.stocksimulation.common.exception.BizException(
        com.lzbsdsg.stocksimulation.common.result.ErrorCode.MARKET_DATA_UNAVAILABLE);
  }
}
