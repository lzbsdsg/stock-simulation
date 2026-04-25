package com.lzbsdsg.stocksimulation.portfolio.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lzbsdsg.stocksimulation.portfolio.domain.entity.FundFlow;
import com.lzbsdsg.stocksimulation.portfolio.domain.repository.FundFlowRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 资金流水仓储实现 */
@Repository
@RequiredArgsConstructor
public class FundFlowRepositoryImpl implements FundFlowRepository {

  private static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");

  private final FundFlowMapper fundFlowMapper;

  @Override
  public void save(FundFlow fundFlow) {
    FundFlowDO d = toDO(fundFlow);
    fundFlowMapper.insert(d);
    fundFlow.setId(d.getId());
  }

  @Override
  public List<FundFlow> findByUserId(Long userId, int page, int size) {
    Page<FundFlowDO> p =
        fundFlowMapper.selectPage(
            new Page<>(page, size),
            new LambdaQueryWrapper<FundFlowDO>()
                .eq(FundFlowDO::getUserId, userId)
                .orderByDesc(FundFlowDO::getCreatedAt));
    return p.getRecords().stream().map(this::toDomain).collect(Collectors.toList());
  }

  @Override
  public long countByUserId(Long userId) {
    return fundFlowMapper.selectCount(
        new LambdaQueryWrapper<FundFlowDO>().eq(FundFlowDO::getUserId, userId));
  }

  @Override
  public List<FundFlow> findByUserIdAndCreatedAtBetween(
      Long userId, LocalDateTime from, LocalDateTime to) {
    List<FundFlowDO> list =
        fundFlowMapper.selectList(
            new LambdaQueryWrapper<FundFlowDO>()
                .eq(FundFlowDO::getUserId, userId)
                .between(
                    FundFlowDO::getCreatedAt,
                    from.atZone(ZONE_SHANGHAI).toOffsetDateTime(),
                    to.atZone(ZONE_SHANGHAI).toOffsetDateTime()));
    return list.stream().map(this::toDomain).collect(Collectors.toList());
  }

  // ---- Converter ----

  private FundFlow toDomain(FundFlowDO d) {
    FundFlow f = new FundFlow();
    f.setId(d.getId());
    f.setUserId(d.getUserId());
    f.setFlowType(FundFlow.FundFlowType.valueOf(d.getFlowType()));
    f.setAmount(d.getAmount());
    f.setBalanceAfter(d.getBalanceAfter());
    f.setOrderId(d.getOrderId());
    f.setRemark(d.getRemark());
    f.setCreatedAt(
        d.getCreatedAt() == null
            ? null
            : d.getCreatedAt().atZoneSameInstant(ZONE_SHANGHAI).toLocalDateTime());
    return f;
  }

  private FundFlowDO toDO(FundFlow f) {
    FundFlowDO d = new FundFlowDO();
    d.setId(f.getId());
    d.setUserId(f.getUserId());
    d.setFlowType(f.getFlowType().name());
    d.setAmount(f.getAmount());
    d.setBalanceAfter(f.getBalanceAfter());
    d.setOrderId(f.getOrderId());
    d.setRemark(f.getRemark());
    d.setCreatedAt(
        f.getCreatedAt() == null
            ? null
            : f.getCreatedAt().atZone(ZONE_SHANGHAI).toOffsetDateTime());
    return d;
  }
}
