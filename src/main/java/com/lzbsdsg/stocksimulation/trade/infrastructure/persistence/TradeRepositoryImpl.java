package com.lzbsdsg.stocksimulation.trade.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lzbsdsg.stocksimulation.trade.domain.entity.OrderSide;
import com.lzbsdsg.stocksimulation.trade.domain.entity.Trade;
import com.lzbsdsg.stocksimulation.trade.domain.repository.TradeRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 成交记录仓储实现 */
@Repository
@RequiredArgsConstructor
public class TradeRepositoryImpl implements TradeRepository {

  private final TradeMapper tradeMapper;

  @Override
  public void save(Trade trade) {
    TradeDO d = toDO(trade);
    tradeMapper.insert(d);
    trade.setId(d.getId());
  }

  @Override
  public List<Trade> findByUserId(Long userId, int page, int size) {
    Page<TradeDO> p =
        tradeMapper.selectPage(
            new Page<>(page, size),
            new LambdaQueryWrapper<TradeDO>()
                .eq(TradeDO::getUserId, userId)
                .orderByDesc(TradeDO::getTradedAt));
    return p.getRecords().stream().map(this::toDomain).collect(Collectors.toList());
  }

  @Override
  public List<Trade> findByOrderId(Long orderId) {
    List<TradeDO> list =
        tradeMapper.selectList(new LambdaQueryWrapper<TradeDO>().eq(TradeDO::getOrderId, orderId));
    return list.stream().map(this::toDomain).collect(Collectors.toList());
  }

  @Override
  public long countByUserId(Long userId) {
    return tradeMapper.selectCount(
        new LambdaQueryWrapper<TradeDO>().eq(TradeDO::getUserId, userId));
  }

  @Override
  public List<Trade> findByUserIdAndTradedAtBetween(
      Long userId, LocalDateTime from, LocalDateTime to) {
    List<TradeDO> list =
        tradeMapper.selectList(
            new LambdaQueryWrapper<TradeDO>()
                .eq(TradeDO::getUserId, userId)
                .between(TradeDO::getTradedAt, from, to));
    return list.stream().map(this::toDomain).collect(Collectors.toList());
  }

  // ---- Converter ----

  private Trade toDomain(TradeDO d) {
    Trade t = new Trade();
    t.setId(d.getId());
    t.setOrderId(d.getOrderId());
    t.setUserId(d.getUserId());
    t.setStockCode(d.getStockCode());
    t.setStockName(d.getStockName());
    t.setSide(OrderSide.valueOf(d.getSide()));
    t.setTradePrice(d.getTradePrice());
    t.setTradeQuantity(d.getTradeQuantity());
    t.setTradeAmount(d.getTradeAmount());
    t.setCommission(d.getCommission());
    t.setTradedAt(d.getTradedAt());
    return t;
  }

  private TradeDO toDO(Trade t) {
    TradeDO d = new TradeDO();
    d.setId(t.getId());
    d.setOrderId(t.getOrderId());
    d.setUserId(t.getUserId());
    d.setStockCode(t.getStockCode());
    d.setStockName(t.getStockName());
    d.setSide(t.getSide().name());
    d.setTradePrice(t.getTradePrice());
    d.setTradeQuantity(t.getTradeQuantity());
    d.setTradeAmount(t.getTradeAmount());
    d.setCommission(t.getCommission());
    d.setTradedAt(t.getTradedAt());
    return d;
  }
}
