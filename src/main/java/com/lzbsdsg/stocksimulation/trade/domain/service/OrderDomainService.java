package com.lzbsdsg.stocksimulation.trade.domain.service;

import com.lzbsdsg.stocksimulation.market.domain.entity.QuoteSnapshot;
import com.lzbsdsg.stocksimulation.trade.domain.entity.Order;
import com.lzbsdsg.stocksimulation.trade.domain.entity.OrderSide;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;

/**
 * 订单领域服务
 *
 * <p>包含所有交易业务规则校验（纯领域逻辑，不依赖框架注解）。
 */
public class OrderDomainService {

  private static final int MIN_QUANTITY = 100;
  private static final LocalTime MORNING_OPEN = LocalTime.of(9, 30);
  private static final LocalTime MORNING_CLOSE = LocalTime.of(11, 30);
  private static final LocalTime AFTERNOON_OPEN = LocalTime.of(13, 0);
  private static final LocalTime AFTERNOON_CLOSE = LocalTime.of(15, 0);

  /** 校验是否在交易时间内 */
  public boolean isWithinTradingHours(LocalTime now) {
    return isWithinTradingHours(
        now, MORNING_OPEN, MORNING_CLOSE, AFTERNOON_OPEN, AFTERNOON_CLOSE);
  }

  /** 校验是否在交易时间内（自定义时段） */
  public boolean isWithinTradingHours(
      LocalTime now,
      LocalTime morningOpen,
      LocalTime morningClose,
      LocalTime afternoonOpen,
      LocalTime afternoonClose) {
    return !now.isBefore(morningOpen) && !now.isAfter(morningClose)
        || !now.isBefore(afternoonOpen) && !now.isAfter(afternoonClose);
  }

  /** 校验委托数量是否为100的整数倍 */
  public boolean isValidQuantity(int quantity) {
    return quantity >= MIN_QUANTITY && quantity % MIN_QUANTITY == 0;
  }

  /** 校验委托价格是否在涨跌停范围内 */
  public boolean isPriceWithinLimit(BigDecimal price, QuoteSnapshot quote) {
    if (quote.getUpperLimitPrice() == null || quote.getLowerLimitPrice() == null) {
      return true;
    }
    return price.compareTo(quote.getLowerLimitPrice()) >= 0
        && price.compareTo(quote.getUpperLimitPrice()) <= 0;
  }

  /** 计算买入冻结金额 = 委托价 × 数量 + 预估手续费 */
  public BigDecimal calculateFreezeAmount(
      BigDecimal price, int quantity, BigDecimal commissionRate) {
    BigDecimal orderAmount = price.multiply(BigDecimal.valueOf(quantity));
    BigDecimal estimatedFee =
        orderAmount.multiply(commissionRate).setScale(2, RoundingMode.HALF_UP);
    return orderAmount.add(estimatedFee);
  }

  /** 判断是否可成交（买入：委托价 ≥ 市价；卖出：委托价 ≤ 市价） */
  public boolean canMatch(Order order, BigDecimal marketPrice) {
    if (order.getSide() == OrderSide.BUY) {
      return order.getPrice().compareTo(marketPrice) >= 0;
    } else {
      return order.getPrice().compareTo(marketPrice) <= 0;
    }
  }
}
