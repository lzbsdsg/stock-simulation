package com.lzbsdsg.stocksimulation.watchlist.application.vo;

import java.math.BigDecimal;

/** 自选股条目 VO */
public record WatchlistItemVO(
    String stockCode,
    String stockName,
    BigDecimal currentPrice,
    BigDecimal changePercent,
    Integer sortOrder) {}
