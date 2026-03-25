package com.lzbsdsg.stocksimulation.market.infrastructure.persistence;

import com.lzbsdsg.stocksimulation.market.domain.entity.MarketKLineSyncState;
import com.lzbsdsg.stocksimulation.market.domain.repository.MarketKLineSyncStateRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 历史日K同步状态仓储实现 */
@Repository
@RequiredArgsConstructor
public class MarketKLineSyncStateRepositoryImpl implements MarketKLineSyncStateRepository {

  private final MarketKLineSyncStateMapper marketKLineSyncStateMapper;

  @Override
  public Optional<MarketKLineSyncState> findByStockCode(String stockCode) {
    MarketKLineSyncStateDO data = marketKLineSyncStateMapper.selectByStockCode(stockCode);
    return Optional.ofNullable(data).map(this::toDomain);
  }

  @Override
  public void upsert(MarketKLineSyncState syncState) {
    marketKLineSyncStateMapper.upsert(toDO(syncState));
  }

  private MarketKLineSyncState toDomain(MarketKLineSyncStateDO data) {
    MarketKLineSyncState state = new MarketKLineSyncState();
    state.setStockCode(data.getStockCode());
    state.setLastSyncDate(data.getLastSyncDate());
    state.setLastBarDate(data.getLastBarDate());
    return state;
  }

  private MarketKLineSyncStateDO toDO(MarketKLineSyncState state) {
    MarketKLineSyncStateDO data = new MarketKLineSyncStateDO();
    data.setStockCode(state.getStockCode());
    data.setLastSyncDate(state.getLastSyncDate());
    data.setLastBarDate(state.getLastBarDate());
    return data;
  }
}
