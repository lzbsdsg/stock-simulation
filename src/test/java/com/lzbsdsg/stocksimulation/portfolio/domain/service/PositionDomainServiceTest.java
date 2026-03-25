package com.lzbsdsg.stocksimulation.portfolio.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
  void should_apply_buy_fill_and_set_next_trading_day_freeze() {
    Position position = emptyPosition();

    positionDomainService.applyBuyFill(position, 100, new BigDecimal("10.00"), LocalDate.of(2026, 3, 27));

    assertEquals(100, position.getTotalQuantity());
    assertEquals(100, position.getFrozenQuantity());
    assertEquals(new BigDecimal("10.0000"), position.getCostPrice());
    assertEquals(LocalDate.of(2026, 3, 30), position.getFrozenUntil());
  }

  @Test
  void should_apply_sell_fill_from_frozen_quantity() {
    Position position = emptyPosition();
    position.setTotalQuantity(200);
    position.setAvailableQuantity(0);
    position.setFrozenQuantity(200);
    position.setCostPrice(new BigDecimal("8.0000"));
    position.setTotalCost(new BigDecimal("1600.00"));

    positionDomainService.applySellFill(position, 100);

    assertEquals(100, position.getTotalQuantity());
    assertEquals(100, position.getFrozenQuantity());
    assertEquals(new BigDecimal("800.0000"), position.getTotalCost());
  }

  @Test
  void should_throw_when_sell_fill_quantity_exceeds_frozen() {
    Position position = emptyPosition();
    position.setTotalQuantity(100);
    position.setFrozenQuantity(50);

    assertThrows(IllegalStateException.class, () -> positionDomainService.applySellFill(position, 100));
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
