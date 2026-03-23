package com.lzbsdsg.stocksimulation.trade.domain.service;

import com.lzbsdsg.stocksimulation.trade.domain.entity.Order;
import com.lzbsdsg.stocksimulation.trade.domain.entity.OrderSide;
import com.lzbsdsg.stocksimulation.trade.domain.entity.Trade;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 撮合引擎（领域服务）
 *
 * <p>负责将待成交订单与最新行情进行撮合，生成成交记录。 仿真系统简化为：委托价满足条件即刻成交（非真实的订单簿撮合）。
 */
public class MatchEngine {

  /**
   * 尝试撮合订单
   *
   * @param order 待撮合订单
   * @param marketPrice 最新市价
   * @param commissionRate 手续费率
   * @return 成交记录，若不能撮合返回 null
   */
  public Trade tryMatch(Order order, BigDecimal marketPrice, BigDecimal commissionRate) {
    // 判断是否可成交
    if (order.getSide() == OrderSide.BUY && order.getPrice().compareTo(marketPrice) < 0) {
      return null;
    }
    if (order.getSide() == OrderSide.SELL && order.getPrice().compareTo(marketPrice) > 0) {
      return null;
    }

    int matchQty = order.remainingQuantity();
    BigDecimal tradeAmount = marketPrice.multiply(BigDecimal.valueOf(matchQty));
    BigDecimal fee = tradeAmount.multiply(commissionRate).setScale(2, RoundingMode.HALF_UP);

    // 更新订单状态
    order.fill(matchQty, tradeAmount, fee);

    // 生成 Trade 记录
    Trade trade = new Trade();
    trade.setOrderId(order.getId());
    trade.setUserId(order.getUserId());
    trade.setStockCode(order.getStockCode());
    trade.setStockName(order.getStockName());
    trade.setSide(order.getSide());
    trade.setTradePrice(marketPrice);
    trade.setTradeQuantity(matchQty);
    trade.setTradeAmount(tradeAmount);
    trade.setCommission(fee);
    trade.setTradedAt(LocalDateTime.now());

    return trade;
  }
}
