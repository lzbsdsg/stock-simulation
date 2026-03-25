package com.lzbsdsg.stocksimulation.portfolio.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lzbsdsg.stocksimulation.portfolio.domain.entity.AssetSnapshot;
import com.lzbsdsg.stocksimulation.portfolio.domain.repository.AssetSnapshotRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 资产快照仓储实现 */
@Repository
@RequiredArgsConstructor
public class AssetSnapshotRepositoryImpl implements AssetSnapshotRepository {

  private static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");

  private final AssetSnapshotMapper assetSnapshotMapper;

  @Override
  public void save(AssetSnapshot snapshot) {
    AssetSnapshotDO d = toDO(snapshot);
    assetSnapshotMapper.insert(d);
    snapshot.setId(d.getId());
  }

  @Override
  public Optional<AssetSnapshot> findByUserIdAndDate(Long userId, LocalDate date) {
    AssetSnapshotDO d =
        assetSnapshotMapper.selectOne(
            new LambdaQueryWrapper<AssetSnapshotDO>()
                .eq(AssetSnapshotDO::getUserId, userId)
                .eq(AssetSnapshotDO::getSnapshotDate, date));
    return Optional.ofNullable(d).map(this::toDomain);
  }

  @Override
  public List<AssetSnapshot> findByUserIdBetween(Long userId, LocalDate from, LocalDate to) {
    List<AssetSnapshotDO> list =
        assetSnapshotMapper.selectList(
            new LambdaQueryWrapper<AssetSnapshotDO>()
                .eq(AssetSnapshotDO::getUserId, userId)
                .between(AssetSnapshotDO::getSnapshotDate, from, to)
                .orderByAsc(AssetSnapshotDO::getSnapshotDate));
    return list.stream().map(this::toDomain).collect(Collectors.toList());
  }

  @Override
  public Optional<AssetSnapshot> findLatestBefore(Long userId, LocalDate snapshotDate) {
    return Optional.ofNullable(assetSnapshotMapper.selectLatestBefore(userId, snapshotDate))
        .map(this::toDomain);
  }

  // ---- Converter ----

  private AssetSnapshot toDomain(AssetSnapshotDO d) {
    AssetSnapshot s = new AssetSnapshot();
    s.setId(d.getId());
    s.setUserId(d.getUserId());
    s.setSnapshotDate(d.getSnapshotDate());
    s.setTotalAssets(d.getTotalAssets());
    s.setAvailableBalance(d.getAvailableBalance());
    s.setMarketValue(d.getMarketValue());
    s.setDailyProfit(d.getDailyProfit());
    s.setCumulativeProfitRate(d.getCumulativeProfitRate());
    s.setCreatedAt(
        d.getCreatedAt() == null
            ? null
            : d.getCreatedAt().atZoneSameInstant(ZONE_SHANGHAI).toLocalDateTime());
    return s;
  }

  private AssetSnapshotDO toDO(AssetSnapshot s) {
    AssetSnapshotDO d = new AssetSnapshotDO();
    d.setId(s.getId());
    d.setUserId(s.getUserId());
    d.setSnapshotDate(s.getSnapshotDate());
    d.setTotalAssets(s.getTotalAssets());
    d.setAvailableBalance(s.getAvailableBalance());
    d.setMarketValue(s.getMarketValue());
    d.setDailyProfit(s.getDailyProfit());
    d.setCumulativeProfitRate(s.getCumulativeProfitRate());
    d.setCreatedAt(
        s.getCreatedAt() == null ? null : s.getCreatedAt().atZone(ZONE_SHANGHAI).toOffsetDateTime());
    return d;
  }
}
