package com.lzbsdsg.stocksimulation.portfolio.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lzbsdsg.stocksimulation.portfolio.domain.entity.Position;
import com.lzbsdsg.stocksimulation.portfolio.domain.repository.PositionRepository;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 持仓仓储实现 */
@Repository
@RequiredArgsConstructor
public class PositionRepositoryImpl implements PositionRepository {

  private final PositionMapper positionMapper;

  @Override
  public Optional<Position> findByUserIdAndStockCode(Long userId, String stockCode) {
    PositionDO d =
        positionMapper.selectOne(
            new LambdaQueryWrapper<PositionDO>()
                .eq(PositionDO::getUserId, userId)
                .eq(PositionDO::getStockCode, stockCode));
    return Optional.ofNullable(d).map(this::toDomain);
  }

  @Override
  public Optional<Position> findByUserIdAndStockCodeForUpdate(Long userId, String stockCode) {
    PositionDO d = positionMapper.selectByUserIdAndStockCodeForUpdate(userId, stockCode);
    return Optional.ofNullable(d).map(this::toDomain);
  }

  @Override
  public List<Position> findByUserId(Long userId) {
    List<PositionDO> list =
        positionMapper.selectList(
            new LambdaQueryWrapper<PositionDO>().eq(PositionDO::getUserId, userId));
    return list.stream().map(this::toDomain).collect(Collectors.toList());
  }

  @Override
  public void save(Position position) {
    PositionDO d = toDO(position);
    positionMapper.insert(d);
    position.setId(d.getId());
  }

  @Override
  public boolean updateWithVersion(Position position) {
    PositionDO d = toDO(position);
    int rows = positionMapper.updateById(d);
    return rows > 0;
  }

  @Override
  public void deleteById(Long id) {
    positionMapper.deleteById(id);
  }

  // ---- Converter ----

  private Position toDomain(PositionDO d) {
    Position p = new Position();
    p.setId(d.getId());
    p.setUserId(d.getUserId());
    p.setStockCode(d.getStockCode());
    p.setStockName(d.getStockName());
    p.setTotalQuantity(d.getTotalQuantity());
    p.setAvailableQuantity(d.getAvailableQuantity());
    p.setFrozenQuantity(d.getFrozenQuantity());
    p.setCostPrice(d.getCostPrice());
    p.setTotalCost(d.getTotalCost());
    p.setFrozenUntil(d.getFrozenUntil());
    p.setVersion(d.getVersion());
    p.setCreatedAt(d.getCreatedAt());
    p.setUpdatedAt(d.getUpdatedAt());
    return p;
  }

  private PositionDO toDO(Position p) {
    PositionDO d = new PositionDO();
    d.setId(p.getId());
    d.setUserId(p.getUserId());
    d.setStockCode(p.getStockCode());
    d.setStockName(p.getStockName());
    d.setTotalQuantity(p.getTotalQuantity());
    d.setAvailableQuantity(p.getAvailableQuantity());
    d.setFrozenQuantity(p.getFrozenQuantity());
    d.setCostPrice(p.getCostPrice());
    d.setTotalCost(p.getTotalCost());
    d.setFrozenUntil(p.getFrozenUntil());
    d.setVersion(p.getVersion());
    d.setCreatedAt(p.getCreatedAt());
    d.setUpdatedAt(p.getUpdatedAt());
    return d;
  }
}
