package com.lzbsdsg.stocksimulation.market.application.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 市场整体摘要 */
public record MarketSummaryVO(
    long riseCount,
    long fallCount,
    long flatCount,
    long totalVolume,
    BigDecimal totalAmount,
    BigDecimal avgChangePercent,
    int sampleSize,
    LocalDateTime timestamp) {}
