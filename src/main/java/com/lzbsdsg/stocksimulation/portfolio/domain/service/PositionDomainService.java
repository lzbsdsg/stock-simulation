package com.lzbsdsg.stocksimulation.portfolio.domain.service;

import com.lzbsdsg.stocksimulation.portfolio.domain.entity.Position;
import java.math.BigDecimal;

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
}
