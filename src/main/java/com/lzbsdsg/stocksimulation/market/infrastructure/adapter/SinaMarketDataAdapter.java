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

/**
 * 新浪财经行情适配器
 *
 * <p>调用新浪实时行情 HTTP 接口，解析返回数据并转换为领域对象。 优先级最高（@Order(1)）。
 */
@Slf4j
@Order(1)
@Component
public class SinaMarketDataAdapter implements MarketDataProvider {

  private static final String SINA_QUOTE_URL = "https://hq.sinajs.cn/list=";

  @Override
  public QuoteSnapshot getQuote(String stockCode) {
    // TODO: 发起 HTTP 请求到新浪行情接口
    // URL 示例: https://hq.sinajs.cn/list=sh600519
    // 解析返回文本，提取各字段填充 QuoteSnapshot
    throw new UnsupportedOperationException("SinaMarketDataAdapter.getQuote not implemented");
  }

  @Override
  public List<KLinePoint> getKLine(
      String stockCode, KLinePeriod period, LocalDate from, LocalDate to) {
    // TODO: 新浪K线数据接口适配
    throw new UnsupportedOperationException("SinaMarketDataAdapter.getKLine not implemented");
  }

  @Override
  public List<QuoteSnapshot> batchGetQuotes(List<String> stockCodes) {
    // TODO: 新浪支持批量查询，逗号拼接股票代码
    // URL 示例: https://hq.sinajs.cn/list=sh600519,sz000001
    throw new UnsupportedOperationException("SinaMarketDataAdapter.batchGetQuotes not implemented");
  }

  @Override
  public boolean isAvailable() {
    // TODO: 健康检查，尝试请求一只股票，timeout 2s
    return true;
  }
}
