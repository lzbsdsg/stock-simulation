package com.lzbsdsg.stocksimulation.trade.application.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 成交记录 VO */
public record TradeVO(
    Long tradeId,
    Long orderId,
    String stockCode,
    String stockName,
    String side,
    BigDecimal tradePrice,
    Integer tradeQuantity,
    BigDecimal tradeAmount,
    BigDecimal commission,
    LocalDateTime tradedAt) {}
