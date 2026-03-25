package com.lzbsdsg.stocksimulation.portfolio.application.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 收益曲线点位 VO。 */
public record EquityCurvePointVO(LocalDate date, BigDecimal totalAssets, BigDecimal profitRate) {}

