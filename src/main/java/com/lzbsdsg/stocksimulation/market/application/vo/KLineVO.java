package com.lzbsdsg.stocksimulation.market.application.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

/** K线数据 VO */
public record KLineVO(
    LocalDate date,
    BigDecimal open,
    BigDecimal close,
    BigDecimal high,
    BigDecimal low,
    Long volume,
    BigDecimal amount) {}
