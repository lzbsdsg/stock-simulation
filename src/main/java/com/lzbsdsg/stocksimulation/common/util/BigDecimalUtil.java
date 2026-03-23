package com.lzbsdsg.stocksimulation.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 金额安全运算工具类
 *
 * <p>所有金额运算使用 BigDecimal，避免浮点精度问题。
 */
public final class BigDecimalUtil {

  /** 金额保留2位小数 */
  public static final int MONEY_SCALE = 2;

  /** 价格保留4位小数 */
  public static final int PRICE_SCALE = 4;

  /** 费率保留6位小数 */
  public static final int RATE_SCALE = 6;

  private BigDecimalUtil() {}

  /** 金额乘法（结果保留2位小数，四舍五入） */
  public static BigDecimal multiply(BigDecimal a, BigDecimal b) {
    return a.multiply(b).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
  }

  /** 金额除法（结果保留4位小数，四舍五入） */
  public static BigDecimal divide(BigDecimal a, BigDecimal b) {
    return a.divide(b, PRICE_SCALE, RoundingMode.HALF_UP);
  }

  /** 取较大值 */
  public static BigDecimal max(BigDecimal a, BigDecimal b) {
    return a.compareTo(b) >= 0 ? a : b;
  }

  /** 是否大于等于 */
  public static boolean gte(BigDecimal a, BigDecimal b) {
    return a.compareTo(b) >= 0;
  }

  /** 是否大于 */
  public static boolean gt(BigDecimal a, BigDecimal b) {
    return a.compareTo(b) > 0;
  }

  /** 是否小于等于 */
  public static boolean lte(BigDecimal a, BigDecimal b) {
    return a.compareTo(b) <= 0;
  }

  /** 百分比计算: (value - base) / base * 100 */
  public static BigDecimal percentChange(BigDecimal value, BigDecimal base) {
    if (base.compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ZERO;
    }
    return value
        .subtract(base)
        .divide(base, PRICE_SCALE, RoundingMode.HALF_UP)
        .multiply(BigDecimal.valueOf(100))
        .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
  }
}
