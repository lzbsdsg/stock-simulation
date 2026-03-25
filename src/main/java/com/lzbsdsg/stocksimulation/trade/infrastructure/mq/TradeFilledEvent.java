package com.lzbsdsg.stocksimulation.trade.infrastructure.mq;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 成交事件消息体。 */
public record TradeFilledEvent(
    Long orderId,
    Long tradeId,
    Long userId,
    String stockCode,
    String stockName,
    String side,
    BigDecimal tradePrice,
    Integer tradeQuantity,
    BigDecimal tradeAmount,
    BigDecimal commission,
    LocalDateTime tradedAt) {}
