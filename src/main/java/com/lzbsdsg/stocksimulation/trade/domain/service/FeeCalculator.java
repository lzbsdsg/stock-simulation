package com.lzbsdsg.stocksimulation.trade.domain.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 手续费计算器（领域服务）
 *
 * <p>A股仿真交易典型费率： - 佣金: 万分之三（最低5元） - 印花税: 千分之一（仅卖出收取） - 过户费: 万分之零点二
 */
public class FeeCalculator {

  private static final BigDecimal COMMISSION_RATE = new BigDecimal("0.0003");
  private static final BigDecimal MIN_COMMISSION = new BigDecimal("5.00");
  private static final BigDecimal STAMP_TAX_RATE = new BigDecimal("0.001");
  private static final BigDecimal TRANSFER_FEE_RATE = new BigDecimal("0.00002");

  /** 计算买入手续费（佣金 + 过户费） */
  public BigDecimal calculateBuyFee(BigDecimal tradeAmount) {
    BigDecimal commission = tradeAmount.multiply(COMMISSION_RATE).setScale(2, RoundingMode.HALF_UP);
    if (commission.compareTo(MIN_COMMISSION) < 0) {
      commission = MIN_COMMISSION;
    }
    BigDecimal transferFee =
        tradeAmount.multiply(TRANSFER_FEE_RATE).setScale(2, RoundingMode.HALF_UP);
    return commission.add(transferFee);
  }

  /** 计算卖出手续费（佣金 + 印花税 + 过户费） */
  public BigDecimal calculateSellFee(BigDecimal tradeAmount) {
    BigDecimal commission = tradeAmount.multiply(COMMISSION_RATE).setScale(2, RoundingMode.HALF_UP);
    if (commission.compareTo(MIN_COMMISSION) < 0) {
      commission = MIN_COMMISSION;
    }
    BigDecimal stampTax = tradeAmount.multiply(STAMP_TAX_RATE).setScale(2, RoundingMode.HALF_UP);
    BigDecimal transferFee =
        tradeAmount.multiply(TRANSFER_FEE_RATE).setScale(2, RoundingMode.HALF_UP);
    return commission.add(stampTax).add(transferFee);
  }

  /** 预估买入手续费率（用于下单冻结金额计算） */
  public BigDecimal estimateBuyCommissionRate() {
    return COMMISSION_RATE.add(TRANSFER_FEE_RATE);
  }
}
