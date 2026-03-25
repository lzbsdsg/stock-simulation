package com.lzbsdsg.stocksimulation.market.domain.repository;

import com.lzbsdsg.stocksimulation.market.domain.entity.MarketKLineSyncState;
import java.util.Optional;

/** 历史日K同步状态仓储 */
public interface MarketKLineSyncStateRepository {

  Optional<MarketKLineSyncState> findByStockCode(String stockCode);

  void upsert(MarketKLineSyncState syncState);
}
