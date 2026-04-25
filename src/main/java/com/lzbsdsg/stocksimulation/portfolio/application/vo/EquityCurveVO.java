package com.lzbsdsg.stocksimulation.portfolio.application.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 收益曲线 VO。 */
public record EquityCurveVO(
    List<EquityCurvePointVO> points, BigDecimal maxDrawdown, LocalDate maxDrawdownDate) {}
