package com.lzbsdsg.stocksimulation.portfolio.application.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 持仓 VO */
public record PositionVO(
    Long positionId,
    String stockCode,
    String stockName,
    /** 总数量 */
    Integer totalQuantity,
    /** 可卖数量 */
    Integer availableQuantity,
    /** 冻结数量（T+1 冻结） */
    Integer frozenQuantity,
    /** 成本价 */
    BigDecimal costPrice,
    /** 当前价 */
    BigDecimal currentPrice,
    /** 持仓市值 */
    BigDecimal marketValue,
    /** 持仓盈亏 */
    BigDecimal profit,
    /** 持仓盈亏率 % */
    BigDecimal profitRate,
    /** 今日盈亏 */
    BigDecimal todayProfit,
    /** 冻结截止日期 */
    LocalDate frozenUntil) {}
