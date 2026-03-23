package com.lzbsdsg.stocksimulation.watchlist.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lzbsdsg.stocksimulation.watchlist.domain.entity.WatchlistItem;
import com.lzbsdsg.stocksimulation.watchlist.domain.repository.WatchlistRepository;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 自选股仓储实现 */
@Repository
@RequiredArgsConstructor
public class WatchlistRepositoryImpl implements WatchlistRepository {

  private final WatchlistMapper watchlistMapper;

  @Override
  public List<WatchlistItem> findByUserId(Long userId) {
    List<WatchlistDO> list =
        watchlistMapper.selectList(
            new LambdaQueryWrapper<WatchlistDO>()
                .eq(WatchlistDO::getUserId, userId)
                .orderByAsc(WatchlistDO::getSortOrder));
    return list.stream().map(this::toDomain).collect(Collectors.toList());
  }

  @Override
  public Optional<WatchlistItem> findByUserIdAndStockCode(Long userId, String stockCode) {
    WatchlistDO d =
        watchlistMapper.selectOne(
            new LambdaQueryWrapper<WatchlistDO>()
                .eq(WatchlistDO::getUserId, userId)
                .eq(WatchlistDO::getStockCode, stockCode));
    return Optional.ofNullable(d).map(this::toDomain);
  }

  @Override
  public long countByUserId(Long userId) {
    return watchlistMapper.selectCount(
        new LambdaQueryWrapper<WatchlistDO>().eq(WatchlistDO::getUserId, userId));
  }

  @Override
  public void save(WatchlistItem item) {
    WatchlistDO d = toDO(item);
    watchlistMapper.insert(d);
    item.setId(d.getId());
  }

  @Override
  public void deleteByUserIdAndStockCode(Long userId, String stockCode) {
    watchlistMapper.delete(
        new LambdaQueryWrapper<WatchlistDO>()
            .eq(WatchlistDO::getUserId, userId)
            .eq(WatchlistDO::getStockCode, stockCode));
  }

  @Override
  public void batchUpdateSort(Long userId, List<WatchlistItem> items) {
    for (WatchlistItem item : items) {
      WatchlistDO d = toDO(item);
      watchlistMapper.updateById(d);
    }
  }

  // ---- Converter ----

  private WatchlistItem toDomain(WatchlistDO d) {
    WatchlistItem w = new WatchlistItem();
    w.setId(d.getId());
    w.setUserId(d.getUserId());
    w.setStockCode(d.getStockCode());
    w.setStockName(d.getStockName());
    w.setSortOrder(d.getSortOrder());
    w.setCreatedAt(d.getCreatedAt());
    return w;
  }

  private WatchlistDO toDO(WatchlistItem w) {
    WatchlistDO d = new WatchlistDO();
    d.setId(w.getId());
    d.setUserId(w.getUserId());
    d.setStockCode(w.getStockCode());
    d.setStockName(w.getStockName());
    d.setSortOrder(w.getSortOrder());
    d.setCreatedAt(w.getCreatedAt());
    return d;
  }
}
