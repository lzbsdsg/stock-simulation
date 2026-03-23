package com.lzbsdsg.stocksimulation.market.infrastructure.adapter;

import com.lzbsdsg.stocksimulation.market.domain.entity.KLinePeriod;
import com.lzbsdsg.stocksimulation.market.domain.entity.KLinePoint;
import com.lzbsdsg.stocksimulation.market.domain.entity.QuoteSnapshot;
import com.lzbsdsg.stocksimulation.market.domain.gateway.MarketDataProvider;
import java.time.LocalDate;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 腾讯财经行情适配器（备用 Provider） */
@Slf4j
@Order(2)
@Component
public class TencentMarketDataAdapter implements MarketDataProvider {

  private static final String TENCENT_QUOTE_URL = "https://qt.gtimg.cn/q=";

  @Override
  public QuoteSnapshot getQuote(String stockCode) {
    // TODO: 调用腾讯行情接口并解析
    throw new UnsupportedOperationException("TencentMarketDataAdapter.getQuote not implemented");
  }

  @Override
  public List<KLinePoint> getKLine(
      String stockCode, KLinePeriod period, LocalDate from, LocalDate to) {
    // TODO: 腾讯K线接口适配
    throw new UnsupportedOperationException("TencentMarketDataAdapter.getKLine not implemented");
  }

  @Override
  public List<QuoteSnapshot> batchGetQuotes(List<String> stockCodes) {
    // TODO: 腾讯支持批量查询
    throw new UnsupportedOperationException(
        "TencentMarketDataAdapter.batchGetQuotes not implemented");
  }

  @Override
  public boolean isAvailable() {
    // TODO: 健康检查
    return true;
  }
}
