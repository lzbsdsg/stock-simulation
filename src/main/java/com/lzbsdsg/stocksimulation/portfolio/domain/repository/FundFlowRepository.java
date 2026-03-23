package com.lzbsdsg.stocksimulation.portfolio.domain.repository;

import com.lzbsdsg.stocksimulation.portfolio.domain.entity.FundFlow;
import java.time.LocalDateTime;
import java.util.List;

/** 资金流水仓储接口（domain 层定义） */
public interface FundFlowRepository {

  void save(FundFlow fundFlow);

  List<FundFlow> findByUserId(Long userId, int page, int size);

  long countByUserId(Long userId);

  List<FundFlow> findByUserIdAndCreatedAtBetween(Long userId, LocalDateTime from, LocalDateTime to);
}
