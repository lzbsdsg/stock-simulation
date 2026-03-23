package com.lzbsdsg.stocksimulation.market.application;

import com.lzbsdsg.stocksimulation.market.application.vo.KLineVO;
import com.lzbsdsg.stocksimulation.market.application.vo.QuoteVO;
import com.lzbsdsg.stocksimulation.market.domain.entity.KLinePeriod;
import com.lzbsdsg.stocksimulation.market.domain.entity.KLinePoint;
import com.lzbsdsg.stocksimulation.market.domain.entity.QuoteSnapshot;
import com.lzbsdsg.stocksimulation.market.domain.service.MarketDataFacade;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 行情应用服务 */
@Service
@RequiredArgsConstructor
public class MarketApplicationService {

  private final MarketDataFacade marketDataFacade;

  public QuoteVO getQuote(String stockCode) {
    QuoteSnapshot snapshot = marketDataFacade.getQuote(stockCode);
    return toQuoteVO(snapshot);
  }

  public List<QuoteVO> batchGetQuotes(List<String> stockCodes) {
    List<QuoteSnapshot> snapshots = marketDataFacade.batchGetQuotes(stockCodes);
    return snapshots.stream().map(this::toQuoteVO).collect(Collectors.toList());
  }

  public List<KLineVO> getKLine(String stockCode, String period, LocalDate from, LocalDate to) {
    KLinePeriod kLinePeriod = KLinePeriod.valueOf(period);
    List<KLinePoint> points = marketDataFacade.getKLine(stockCode, kLinePeriod, from, to);
    return points.stream().map(this::toKLineVO).collect(Collectors.toList());
  }

  public List<QuoteVO> searchStock(String keyword) {
    // TODO: 搜索股票信息（代码/名称模糊匹配），返回行情快照
    return List.of();
  }

  // ---- Converter ----

  private QuoteVO toQuoteVO(QuoteSnapshot s) {
    return new QuoteVO(
        s.getStockCode(),
        s.getStockName(),
        s.getCurrentPrice(),
        s.getOpenPrice(),
        s.getClosePrice(),
        s.getHighPrice(),
        s.getLowPrice(),
        s.getVolume(),
        s.getAmount(),
        s.getChangePercent(),
        s.getTimestamp());
  }

  private KLineVO toKLineVO(KLinePoint p) {
    return new KLineVO(
        p.getDate(),
        p.getOpen(),
        p.getClose(),
        p.getHigh(),
        p.getLow(),
        p.getVolume(),
        p.getAmount());
  }
}
