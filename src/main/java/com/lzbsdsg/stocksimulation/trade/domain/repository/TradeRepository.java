package com.lzbsdsg.stocksimulation.trade.domain.repository;

import com.lzbsdsg.stocksimulation.trade.domain.entity.Trade;
import java.time.LocalDateTime;
import java.util.List;

/** 成交记录仓储接口（domain 层定义） */
public interface TradeRepository {

  void save(Trade trade);

  List<Trade> findByUserId(Long userId, int page, int size);

  List<Trade> findByOrderId(Long orderId);

  long countByUserId(Long userId);

  List<Trade> findByUserIdAndTradedAtBetween(Long userId, LocalDateTime from, LocalDateTime to);
}
