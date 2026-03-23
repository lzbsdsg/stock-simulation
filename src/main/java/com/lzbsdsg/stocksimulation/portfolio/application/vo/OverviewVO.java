package com.lzbsdsg.stocksimulation.portfolio.application.vo;

import java.math.BigDecimal;

/** 资产总览 VO */
public record OverviewVO(
    /** 总资产 */
    BigDecimal totalAssets,
    /** 可用资金 */
    BigDecimal availableBalance,
    /** 冻结资金 */
    BigDecimal frozenBalance,
    /** 持仓市值 */
    BigDecimal marketValue,
    /** 初始资金 */
    BigDecimal initialBalance,
    /** 总收益 */
    BigDecimal totalProfit,
    /** 总收益率 % */
    BigDecimal totalProfitRate,
    /** 当日盈亏 */
    BigDecimal todayProfit,
    /** 当日盈亏率 % */
    BigDecimal todayProfitRate) {}
