package com.lzbsdsg.stocksimulation.trade.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.lzbsdsg.stocksimulation.trade.domain.entity.Order;
import com.lzbsdsg.stocksimulation.trade.domain.entity.OrderSide;
import com.lzbsdsg.stocksimulation.trade.domain.entity.OrderStatus;
import com.lzbsdsg.stocksimulation.trade.domain.entity.OrderType;
import com.lzbsdsg.stocksimulation.trade.domain.entity.Trade;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 撮合引擎单元测试。 */
class MatchEngineTest {

  private MatchEngine matchEngine;

  @BeforeEach
  void setUp() {
    matchEngine = new MatchEngine();
  }

  @Test
  void should_match_buy_order_when_order_price_gte_market_price() {
    Order order = pendingOrder(OrderSide.BUY, "10.00", 100);

    Trade trade = matchEngine.tryMatch(order, new BigDecimal("9.95"), new BigDecimal("5.00"));

    assertNotNull(trade);
    assertEquals(OrderStatus.FILLED, order.getStatus());
    assertEquals(100, order.getFilledQuantity());
  }

  @Test
  void should_match_sell_order_when_order_price_lte_market_price() {
    Order order = pendingOrder(OrderSide.SELL, "10.00", 100);

    Trade trade = matchEngine.tryMatch(order, new BigDecimal("10.20"), new BigDecimal("5.20"));

    assertNotNull(trade);
    assertEquals(OrderStatus.FILLED, order.getStatus());
    assertEquals(100, order.getFilledQuantity());
  }

  @Test
  void should_not_match_buy_order_when_order_price_lt_market_price() {
    Order order = pendingOrder(OrderSide.BUY, "10.00", 100);

    Trade trade = matchEngine.tryMatch(order, new BigDecimal("10.01"), new BigDecimal("5.00"));

    assertNull(trade);
    assertEquals(OrderStatus.PENDING, order.getStatus());
    assertEquals(0, order.getFilledQuantity());
  }

  @Test
  void should_not_match_sell_order_when_order_price_gt_market_price() {
    Order order = pendingOrder(OrderSide.SELL, "10.00", 100);

    Trade trade = matchEngine.tryMatch(order, new BigDecimal("9.99"), new BigDecimal("5.00"));

    assertNull(trade);
    assertEquals(OrderStatus.PENDING, order.getStatus());
    assertEquals(0, order.getFilledQuantity());
  }

  @Test
  void should_update_order_fill_amount_and_commission_after_match() {
    Order order = pendingOrder(OrderSide.BUY, "10.00", 200);

    Trade trade = matchEngine.tryMatch(order, new BigDecimal("9.50"), new BigDecimal("8.88"));

    assertNotNull(trade);
    assertEquals(new BigDecimal("1900.00"), order.getFilledAmount());
    assertEquals(new BigDecimal("8.88"), order.getCommission());
  }

  @Test
  void should_create_trade_record_with_expected_fields() {
    Order order = pendingOrder(OrderSide.SELL, "10.00", 300);
    order.setId(9001L);
    order.setUserId(1001L);
    order.setStockCode("sh600519");
    order.setStockName("贵州茅台");

    Trade trade = matchEngine.tryMatch(order, new BigDecimal("10.10"), new BigDecimal("11.23"));

    assertNotNull(trade);
    assertEquals(9001L, trade.getOrderId());
    assertEquals(1001L, trade.getUserId());
    assertEquals("sh600519", trade.getStockCode());
    assertEquals("贵州茅台", trade.getStockName());
    assertEquals(OrderSide.SELL, trade.getSide());
    assertEquals(new BigDecimal("10.10"), trade.getTradePrice());
    assertEquals(300, trade.getTradeQuantity());
    assertEquals(new BigDecimal("3030.00"), trade.getTradeAmount());
    assertEquals(new BigDecimal("11.23"), trade.getCommission());
  }

  private Order pendingOrder(OrderSide side, String price, int quantity) {
    Order order = new Order();
    order.setId(1L);
    order.setUserId(1001L);
    order.setStockCode("sh600519");
    order.setStockName("贵州茅台");
    order.setSide(side);
    order.setOrderType(OrderType.LIMIT);
    order.setStatus(OrderStatus.PENDING);
    order.setPrice(new BigDecimal(price));
    order.setQuantity(quantity);
    order.setFilledQuantity(0);
    order.setFilledAmount(BigDecimal.ZERO);
    order.setCommission(BigDecimal.ZERO);
    return order;
  }
}
