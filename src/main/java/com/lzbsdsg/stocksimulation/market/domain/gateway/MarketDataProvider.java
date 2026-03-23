package com.lzbsdsg.stocksimulation.market.domain.gateway;

import com.lzbsdsg.stocksimulation.market.domain.entity.KLinePeriod;
import com.lzbsdsg.stocksimulation.market.domain.entity.KLinePoint;
import com.lzbsdsg.stocksimulation.market.domain.entity.QuoteSnapshot;
import java.time.LocalDate;
import java.util.List;

/**
 * 行情数据提供者接口（域网关）
 *
 * <p>不同数据源（新浪、腾讯、Mock）各自实现此接口。
 */
public interface MarketDataProvider {

  /** 获取单只股票实时行情快照 */
  QuoteSnapshot getQuote(String stockCode);

  /** 获取K线数据 */
  List<KLinePoint> getKLine(String stockCode, KLinePeriod period, LocalDate from, LocalDate to);

  /** 批量获取行情快照（一次最多 50 只） */
  List<QuoteSnapshot> batchGetQuotes(List<String> stockCodes);

  /** 健康检查：当前 Provider 是否可用 */
  boolean isAvailable();
}
