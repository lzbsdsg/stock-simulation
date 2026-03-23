package com.lzbsdsg.stocksimulation.market.domain.repository;

import com.lzbsdsg.stocksimulation.market.domain.entity.StockInfo;
import java.util.List;
import java.util.Optional;

/** 股票信息仓储接口（domain 层定义） */
public interface StockInfoRepository {

  Optional<StockInfo> findByStockCode(String stockCode);

  List<StockInfo> searchByKeyword(String keyword, int limit);

  List<StockInfo> findAllListed();

  void save(StockInfo stockInfo);

  void batchSave(List<StockInfo> stockInfos);
}
