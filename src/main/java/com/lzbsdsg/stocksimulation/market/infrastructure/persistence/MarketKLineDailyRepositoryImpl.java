package com.lzbsdsg.stocksimulation.market.infrastructure.persistence;

import com.lzbsdsg.stocksimulation.market.domain.entity.KLinePoint;
import com.lzbsdsg.stocksimulation.market.domain.repository.MarketKLineDailyRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 历史日K仓储实现 */
@Repository
@RequiredArgsConstructor
public class MarketKLineDailyRepositoryImpl implements MarketKLineDailyRepository {

  private final MarketKLineDailyMapper marketKLineDailyMapper;

  @Override
  public List<KLinePoint> findByStockCodeAndDateRange(String stockCode, LocalDate from, LocalDate to) {
    return marketKLineDailyMapper.selectByStockCodeAndDateRange(stockCode, from, to).stream()
        .map(this::toDomain)
        .collect(Collectors.toList());
  }

  @Override
  public Optional<LocalDate> findLatestTradeDate(String stockCode) {
    return Optional.ofNullable(marketKLineDailyMapper.selectLatestTradeDate(stockCode));
  }

  @Override
  public Optional<LocalDate> findEarliestTradeDate(String stockCode) {
    return Optional.ofNullable(marketKLineDailyMapper.selectEarliestTradeDate(stockCode));
  }

  @Override
  public void upsertBatch(String stockCode, List<KLinePoint> points, String source) {
    if (points == null || points.isEmpty()) {
      return;
    }
    List<MarketKLineDailyDO> rows =
        points.stream().map(point -> toDO(stockCode, point, source)).collect(Collectors.toList());
    marketKLineDailyMapper.upsertBatch(rows);
  }

  private KLinePoint toDomain(MarketKLineDailyDO data) {
    KLinePoint point = new KLinePoint();
    point.setDate(data.getTradeDate());
    point.setOpen(data.getOpenPrice());
    point.setClose(data.getClosePrice());
    point.setHigh(data.getHighPrice());
    point.setLow(data.getLowPrice());
    point.setVolume(data.getVolume());
    point.setAmount(data.getAmount());
    return point;
  }

  private MarketKLineDailyDO toDO(String stockCode, KLinePoint point, String source) {
    MarketKLineDailyDO data = new MarketKLineDailyDO();
    data.setStockCode(stockCode);
    data.setTradeDate(point.getDate());
    data.setOpenPrice(point.getOpen());
    data.setClosePrice(point.getClose());
    data.setHighPrice(point.getHigh());
    data.setLowPrice(point.getLow());
    data.setVolume(point.getVolume() == null ? 0L : point.getVolume());
    data.setAmount(point.getAmount() == null ? java.math.BigDecimal.ZERO : point.getAmount());
    data.setSource(source);
    return data;
  }
}
