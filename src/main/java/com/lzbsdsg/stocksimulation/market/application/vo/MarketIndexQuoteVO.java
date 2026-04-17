package com.lzbsdsg.stocksimulation.market.application.vo;

import java.math.BigDecimal;

/** 大盘指数行情 VO */
public record MarketIndexQuoteVO(
    String stockCode,
    String stockName,
    BigDecimal currentPrice,
    BigDecimal changeAmount,
    BigDecimal changePercent,
    Long volume,
    BigDecimal amount) {}
