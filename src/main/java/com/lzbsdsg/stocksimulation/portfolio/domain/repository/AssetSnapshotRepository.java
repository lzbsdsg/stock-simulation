package com.lzbsdsg.stocksimulation.portfolio.domain.repository;

import com.lzbsdsg.stocksimulation.portfolio.domain.entity.AssetSnapshot;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** 资产快照仓储接口（domain 层定义） */
public interface AssetSnapshotRepository {

  void save(AssetSnapshot snapshot);

  Optional<AssetSnapshot> findByUserIdAndDate(Long userId, LocalDate date);

  List<AssetSnapshot> findByUserIdBetween(Long userId, LocalDate from, LocalDate to);
}
