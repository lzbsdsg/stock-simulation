package com.lzbsdsg.stocksimulation.trade.application.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 委托订单 VO */
public record OrderVO(
    Long orderId,
    String clientOrderId,
    String stockCode,
    String stockName,
    String side,
    String orderType,
    String status,
    BigDecimal price,
    Integer quantity,
    Integer filledQuantity,
    BigDecimal filledAmount,
    BigDecimal commission,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {}
