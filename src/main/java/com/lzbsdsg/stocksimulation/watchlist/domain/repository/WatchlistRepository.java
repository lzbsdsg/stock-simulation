package com.lzbsdsg.stocksimulation.watchlist.domain.repository;

import com.lzbsdsg.stocksimulation.watchlist.domain.entity.WatchlistItem;
import java.util.List;
import java.util.Optional;

/** 自选股仓储接口（domain 层定义） */
public interface WatchlistRepository {

  List<WatchlistItem> findByUserId(Long userId);

  Optional<WatchlistItem> findByUserIdAndStockCode(Long userId, String stockCode);

  long countByUserId(Long userId);

  void save(WatchlistItem item);

  void deleteByUserIdAndStockCode(Long userId, String stockCode);

  void batchUpdateSort(Long userId, List<WatchlistItem> items);
}
