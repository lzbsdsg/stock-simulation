package com.lzbsdsg.stocksimulation.trade.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** 手续费计算器单元测试。 */
class FeeCalculatorTest {

  private final FeeCalculator feeCalculator = new FeeCalculator();

  @Test
  void should_apply_minimum_commission_for_buy() {
    BigDecimal fee = feeCalculator.calculateBuyFee(new BigDecimal("1000.00"));
    assertEquals(new BigDecimal("5.02"), fee);
  }

  @Test
  void should_calculate_buy_fee_without_minimum_commission() {
    BigDecimal fee = feeCalculator.calculateBuyFee(new BigDecimal("200000.00"));
    assertEquals(new BigDecimal("64.00"), fee);
  }

  @Test
  void should_calculate_sell_fee_with_stamp_tax() {
    BigDecimal fee = feeCalculator.calculateSellFee(new BigDecimal("100000.00"));
    assertEquals(new BigDecimal("132.00"), fee);
  }

  @Test
  void should_apply_minimum_commission_for_sell() {
    BigDecimal fee = feeCalculator.calculateSellFee(new BigDecimal("1000.00"));
    assertEquals(new BigDecimal("6.02"), fee);
  }

  @Test
  void should_keep_precision_for_large_amount() {
    BigDecimal fee = feeCalculator.calculateBuyFee(new BigDecimal("1000000.00"));
    assertEquals(new BigDecimal("320.00"), fee);
  }

  @Test
  void should_expose_estimated_buy_commission_rate() {
    assertEquals(new BigDecimal("0.00032"), feeCalculator.estimateBuyCommissionRate());
  }
}
