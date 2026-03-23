package com.lzbsdsg.stocksimulation.portfolio.application.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 资金流水 VO */
public record FundFlowVO(
    Long flowId,
    /** 流水类型: TRADE_BUY, TRADE_SELL, COMMISSION, FREEZE, UNFREEZE, INITIAL */
    String flowType,
    /** 变动金额（正=入账，负=出账） */
    BigDecimal amount,
    /** 变动后余额 */
    BigDecimal balanceAfter,
    /** 关联订单ID */
    Long orderId,
    /** 备注 */
    String remark,
    LocalDateTime createdAt) {}
