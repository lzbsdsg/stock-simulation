package com.lzbsdsg.stocksimulation.portfolio.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.lzbsdsg.stocksimulation.portfolio.domain.entity.Position;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PositionDomainServiceTest {

  private PositionDomainService positionDomainService;

  @BeforeEach
  void setUp() {
    positionDomainService = new PositionDomainService();
  }

  @Test
  void should_calculate_weighted_average_cost_price_on_buy() {
    Position position = emptyPosition();
    position.setTotalQuantity(100);
    position.setFrozenQuantity(100);
    position.setCostPrice(new BigDecimal("10.0000"));
    position.setTotalCost(new BigDecimal("1000.00"));

    positionDomainService.applyBuyFill(
        position, 100, new BigDecimal("12.0000"), LocalDate.of(2026, 3, 27));

    assertEquals(200, position.getTotalQuantity());
    assertEquals(new BigDecimal("11.0000"), position.getCostPrice());
    assertEquals(new BigDecimal("2200.0000"), position.getTotalCost());
    assertEquals(LocalDate.of(2026, 3, 30), position.getFrozenUntil());
  }

  @Test
  void should_mark_new_buy_position_as_frozen_until_next_trading_day() {
    Position position = emptyPosition();

    positionDomainService.applyBuyFill(
        position, 100, new BigDecimal("10.00"), LocalDate.of(2026, 3, 27));

    assertEquals(100, position.getTotalQuantity());
    assertEquals(100, position.getFrozenQuantity());
    assertEquals(new BigDecimal("10.0000"), position.getCostPrice());
    assertEquals(LocalDate.of(2026, 3, 30), position.getFrozenUntil());
  }

  @Test
  void should_handle_first_buy_as_initial_cost_price() {
    Position position = emptyPosition();

    positionDomainService.applyBuyFill(
        position, 100, new BigDecimal("9.8765"), LocalDate.of(2026, 3, 24));

    assertEquals(new BigDecimal("9.8765"), position.getCostPrice());
    assertEquals(new BigDecimal("987.6500"), position.getTotalCost());
  }

  @Test
  void should_correctly_add_fees_to_cost_when_trade_price_contains_fee() {
    Position position = emptyPosition();

    // 将手续费摊入每股成交价后再入账，校验成本累计精度
    positionDomainService.applyBuyFill(
        position, 100, new BigDecimal("10.0532"), LocalDate.of(2026, 3, 24));

    assertEquals(new BigDecimal("10.0532"), position.getCostPrice());
    assertEquals(new BigDecimal("1005.3200"), position.getTotalCost());
  }

  @Test
  void should_not_change_cost_price_on_sell() {
    Position position = emptyPosition();
    position.setTotalQuantity(200);
    position.setAvailableQuantity(0);
    position.setFrozenQuantity(200);
    position.setCostPrice(new BigDecimal("8.0000"));
    position.setTotalCost(new BigDecimal("1600.00"));

    positionDomainService.applySellFill(position, 100);

    assertEquals(100, position.getTotalQuantity());
    assertEquals(100, position.getFrozenQuantity());
    assertEquals(new BigDecimal("8.0000"), position.getCostPrice());
    assertEquals(new BigDecimal("800.0000"), position.getTotalCost());
  }

  @Test
  void should_reduce_position_to_zero_on_full_sell() {
    Position position = emptyPosition();
    position.setTotalQuantity(100);
    position.setAvailableQuantity(0);
    position.setFrozenQuantity(100);
    position.setCostPrice(new BigDecimal("8.0000"));
    position.setTotalCost(new BigDecimal("800.00"));
    position.setFrozenUntil(LocalDate.of(2026, 3, 30));

    positionDomainService.applySellFill(position, 100);

    assertEquals(0, position.getTotalQuantity());
    assertEquals(0, position.getFrozenQuantity());
    assertEquals(0, position.getAvailableQuantity());
    assertEquals(new BigDecimal("0"), position.getCostPrice());
    assertEquals(new BigDecimal("0"), position.getTotalCost());
    assertNull(position.getFrozenUntil());
  }

  @Test
  void should_throw_when_sell_fill_quantity_exceeds_frozen() {
    Position position = emptyPosition();
    position.setTotalQuantity(100);
    position.setFrozenQuantity(50);

    assertThrows(
        IllegalStateException.class, () -> positionDomainService.applySellFill(position, 100));
  }

  private Position emptyPosition() {
    Position position = new Position();
    position.setUserId(1001L);
    position.setStockCode("sh600519");
    position.setStockName("贵州茅台");
    position.setTotalQuantity(0);
    position.setAvailableQuantity(0);
    position.setFrozenQuantity(0);
    position.setCostPrice(BigDecimal.ZERO);
    position.setTotalCost(BigDecimal.ZERO);
    position.setVersion(0);
    return position;
  }
}
