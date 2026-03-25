package com.lzbsdsg.stocksimulation.market.domain.repository;

import com.lzbsdsg.stocksimulation.market.domain.entity.KLinePoint;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** 历史日K仓储 */
public interface MarketKLineDailyRepository {

  List<KLinePoint> findByStockCodeAndDateRange(String stockCode, LocalDate from, LocalDate to);

  Optional<LocalDate> findLatestTradeDate(String stockCode);

  Optional<LocalDate> findEarliestTradeDate(String stockCode);

  void upsertBatch(String stockCode, List<KLinePoint> points, String source);

  void deleteOlderThan(String stockCode, LocalDate cutoffDateInclusive);
}
