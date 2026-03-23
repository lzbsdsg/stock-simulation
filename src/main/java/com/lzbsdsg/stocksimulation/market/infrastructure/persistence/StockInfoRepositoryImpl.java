package com.lzbsdsg.stocksimulation.market.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lzbsdsg.stocksimulation.market.domain.entity.StockInfo;
import com.lzbsdsg.stocksimulation.market.domain.repository.StockInfoRepository;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 股票信息仓储实现 */
@Repository
@RequiredArgsConstructor
public class StockInfoRepositoryImpl implements StockInfoRepository {

  private final StockInfoMapper stockInfoMapper;

  @Override
  public Optional<StockInfo> findByStockCode(String stockCode) {
    StockInfoDO d =
        stockInfoMapper.selectOne(
            new LambdaQueryWrapper<StockInfoDO>().eq(StockInfoDO::getStockCode, stockCode));
    return Optional.ofNullable(d).map(this::toDomain);
  }

  @Override
  public List<StockInfo> searchByKeyword(String keyword, int limit) {
    List<StockInfoDO> list =
        stockInfoMapper.selectList(
            new LambdaQueryWrapper<StockInfoDO>()
                .like(StockInfoDO::getStockCode, keyword)
                .or()
                .like(StockInfoDO::getStockName, keyword)
                .last("LIMIT " + limit));
    return list.stream().map(this::toDomain).collect(Collectors.toList());
  }

  @Override
  public List<StockInfo> findAllListed() {
    List<StockInfoDO> list =
        stockInfoMapper.selectList(
            new LambdaQueryWrapper<StockInfoDO>().eq(StockInfoDO::getListed, true));
    return list.stream().map(this::toDomain).collect(Collectors.toList());
  }

  @Override
  public void save(StockInfo stockInfo) {
    StockInfoDO d = toDO(stockInfo);
    stockInfoMapper.insert(d);
    stockInfo.setId(d.getId());
  }

  @Override
  public void batchSave(List<StockInfo> stockInfos) {
    // TODO: 使用 MyBatis-Plus 批量插入或手动 batch
    stockInfos.forEach(this::save);
  }

  // ---- Converter ----

  private StockInfo toDomain(StockInfoDO d) {
    StockInfo s = new StockInfo();
    s.setId(d.getId());
    s.setStockCode(d.getStockCode());
    s.setStockName(d.getStockName());
    s.setMarket(d.getMarket());
    s.setBoardType(d.getBoardType());
    s.setIndustry(d.getIndustry());
    s.setListed(d.getListed());
    return s;
  }

  private StockInfoDO toDO(StockInfo s) {
    StockInfoDO d = new StockInfoDO();
    d.setId(s.getId());
    d.setStockCode(s.getStockCode());
    d.setStockName(s.getStockName());
    d.setMarket(s.getMarket());
    d.setBoardType(s.getBoardType());
    d.setIndustry(s.getIndustry());
    d.setListed(s.getListed());
    return d;
  }
}
