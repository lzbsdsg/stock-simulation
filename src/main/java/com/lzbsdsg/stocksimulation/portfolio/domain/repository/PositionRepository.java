package com.lzbsdsg.stocksimulation.portfolio.domain.repository;

import com.lzbsdsg.stocksimulation.portfolio.domain.entity.Position;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** 持仓仓储接口（domain 层定义） */
public interface PositionRepository {

  Optional<Position> findByUserIdAndStockCode(Long userId, String stockCode);

  Optional<Position> findByUserIdAndStockCodeForUpdate(Long userId, String stockCode);

  List<Position> findByUserId(Long userId);

  List<Position> findByUserId(Long userId, int page, int size);

  long countByUserId(Long userId);

  BigDecimal sumCostMarketValueByUserId(Long userId);

  void save(Position position);

  boolean updateWithVersion(Position position);

  void deleteById(Long id);

  int markTodayBoughtPositionsFrozenUntil(LocalDate tradeDate, LocalDate frozenUntil);

  int unfreezeDuePositions(LocalDate today);
}
