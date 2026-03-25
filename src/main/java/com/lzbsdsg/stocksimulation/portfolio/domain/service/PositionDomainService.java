package com.lzbsdsg.stocksimulation.portfolio.domain.service;

import com.lzbsdsg.stocksimulation.portfolio.domain.entity.Position;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * 持仓领域服务
 *
 * <p>持仓相关业务规则（纯域逻辑）。
 */
public class PositionDomainService {

  /** 校验可卖数量是否足够 */
  public boolean hasEnoughAvailable(Position position, int sellQuantity) {
    return position.getAvailableQuantity() != null
        && position.getAvailableQuantity() >= sellQuantity;
  }

  /** 计算持仓盈亏 */
  public BigDecimal calculateProfit(Position position, BigDecimal currentPrice) {
    if (position.getTotalQuantity() == null || position.getTotalQuantity() == 0) {
      return BigDecimal.ZERO;
    }
    BigDecimal marketValue = currentPrice.multiply(BigDecimal.valueOf(position.getTotalQuantity()));
    return marketValue.subtract(position.getTotalCost());
  }

  /** 计算持仓盈亏率 */
  public BigDecimal calculateProfitRate(Position position, BigDecimal currentPrice) {
    if (position.getTotalCost() == null
        || position.getTotalCost().compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ZERO;
    }
    BigDecimal profit = calculateProfit(position, currentPrice);
    return profit
        .divide(position.getTotalCost(), 4, java.math.RoundingMode.HALF_UP)
        .multiply(BigDecimal.valueOf(100));
  }

  /** 买入成交后更新持仓（加权平均成本 + T+1冻结）。 */
  public void applyBuyFill(
      Position position, int quantity, BigDecimal tradePrice, LocalDate tradeDate) {
    position.addPosition(quantity, tradePrice);
    position.setFrozenUntil(nextTradingDate(tradeDate));
  }

  /** 卖出成交后更新持仓（消耗冻结数量，减少总仓）。 */
  public void applySellFill(Position position, int quantity) {
    int frozen = position.getFrozenQuantity() == null ? 0 : position.getFrozenQuantity();
    int total = position.getTotalQuantity() == null ? 0 : position.getTotalQuantity();
    if (frozen < quantity || total < quantity) {
      throw new IllegalStateException("持仓冻结数量不足，无法完成卖出结算");
    }

    position.setFrozenQuantity(frozen - quantity);
    position.setTotalQuantity(total - quantity);

    if (position.getTotalQuantity() > 0) {
      position.setTotalCost(
          position.getCostPrice().multiply(BigDecimal.valueOf(position.getTotalQuantity())));
      return;
    }

    position.setAvailableQuantity(0);
    position.setFrozenQuantity(0);
    position.setTotalCost(BigDecimal.ZERO);
    position.setCostPrice(BigDecimal.ZERO);
    position.setFrozenUntil(null);
  }

  /** 计算下一交易日（跳过周末）。 */
  public LocalDate nextTradingDate(LocalDate date) {
    LocalDate next = date.plusDays(1);
    while (next.getDayOfWeek() == DayOfWeek.SATURDAY || next.getDayOfWeek() == DayOfWeek.SUNDAY) {
      next = next.plusDays(1);
    }
    return next;
  }
}
