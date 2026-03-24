package com.lzbsdsg.stocksimulation.trade.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lzbsdsg.stocksimulation.market.domain.entity.QuoteSnapshot;
import com.lzbsdsg.stocksimulation.trade.domain.entity.Order;
import com.lzbsdsg.stocksimulation.trade.domain.entity.OrderSide;
import java.math.BigDecimal;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

/** 订单领域服务单元测试。 */
class OrderDomainServiceTest {

  private final OrderDomainService orderDomainService = new OrderDomainService();

  @Test
  void should_accept_trading_time_within_sessions() {
    assertTrue(orderDomainService.isWithinTradingHours(LocalTime.of(9, 30)));
    assertTrue(orderDomainService.isWithinTradingHours(LocalTime.of(11, 30)));
    assertTrue(orderDomainService.isWithinTradingHours(LocalTime.of(13, 0)));
    assertTrue(orderDomainService.isWithinTradingHours(LocalTime.of(15, 0)));
  }

  @Test
  void should_reject_trading_time_outside_sessions() {
    assertFalse(orderDomainService.isWithinTradingHours(LocalTime.of(9, 29)));
    assertFalse(orderDomainService.isWithinTradingHours(LocalTime.of(11, 31)));
    assertFalse(orderDomainService.isWithinTradingHours(LocalTime.of(12, 30)));
    assertFalse(orderDomainService.isWithinTradingHours(LocalTime.of(15, 1)));
  }

  @Test
  void should_validate_order_quantity_multiple_of_100() {
    assertTrue(orderDomainService.isValidQuantity(100));
    assertTrue(orderDomainService.isValidQuantity(1000));
    assertFalse(orderDomainService.isValidQuantity(99));
    assertFalse(orderDomainService.isValidQuantity(101));
  }

  @Test
  void should_validate_price_within_limit_range() {
    QuoteSnapshot quote = new QuoteSnapshot();
    quote.setLowerLimitPrice(new BigDecimal("9.00"));
    quote.setUpperLimitPrice(new BigDecimal("11.00"));

    assertTrue(orderDomainService.isPriceWithinLimit(new BigDecimal("9.00"), quote));
    assertTrue(orderDomainService.isPriceWithinLimit(new BigDecimal("10.50"), quote));
    assertTrue(orderDomainService.isPriceWithinLimit(new BigDecimal("11.00"), quote));
    assertFalse(orderDomainService.isPriceWithinLimit(new BigDecimal("8.99"), quote));
    assertFalse(orderDomainService.isPriceWithinLimit(new BigDecimal("11.01"), quote));
  }

  @Test
  void should_allow_price_validation_when_quote_limit_missing() {
    QuoteSnapshot quote = new QuoteSnapshot();
    quote.setLowerLimitPrice(null);
    quote.setUpperLimitPrice(null);

    assertTrue(orderDomainService.isPriceWithinLimit(new BigDecimal("100.00"), quote));
  }

  @Test
  void should_calculate_freeze_amount_including_estimated_fee() {
    BigDecimal freezeAmount =
        orderDomainService.calculateFreezeAmount(
            new BigDecimal("10.00"), 100, new BigDecimal("0.00032"));

    assertEquals(new BigDecimal("1000.32"), freezeAmount);
  }

  @Test
  void should_match_buy_order_when_bid_ge_market_price() {
    Order order = new Order();
    order.setSide(OrderSide.BUY);
    order.setPrice(new BigDecimal("10.00"));

    assertTrue(orderDomainService.canMatch(order, new BigDecimal("9.90")));
    assertTrue(orderDomainService.canMatch(order, new BigDecimal("10.00")));
    assertFalse(orderDomainService.canMatch(order, new BigDecimal("10.01")));
  }

  @Test
  void should_match_sell_order_when_ask_le_market_price() {
    Order order = new Order();
    order.setSide(OrderSide.SELL);
    order.setPrice(new BigDecimal("10.00"));

    assertTrue(orderDomainService.canMatch(order, new BigDecimal("10.00")));
    assertTrue(orderDomainService.canMatch(order, new BigDecimal("10.20")));
    assertFalse(orderDomainService.canMatch(order, new BigDecimal("9.99")));
  }
}
