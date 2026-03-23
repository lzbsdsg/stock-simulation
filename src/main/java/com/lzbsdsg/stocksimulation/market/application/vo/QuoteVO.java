package com.lzbsdsg.stocksimulation.market.application.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 行情快照 VO */
public record QuoteVO(
    String stockCode,
    String stockName,
    BigDecimal currentPrice,
    BigDecimal openPrice,
    BigDecimal closePrice,
    BigDecimal highPrice,
    BigDecimal lowPrice,
    Long volume,
    BigDecimal amount,
    BigDecimal changePercent,
    LocalDateTime timestamp) {}
