package com.lzbsdsg.stocksimulation.portfolio.domain.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/** 资金流水（领域实体） */
@Data
public class FundFlow {

  private Long id;

  private Long userId;

  /** 流水类型 */
  private FundFlowType flowType;

  /** 变动金额（正=入账，负=出账） */
  private BigDecimal amount;

  /** 变动后余额 */
  private BigDecimal balanceAfter;

  /** 关联订单ID */
  private Long orderId;

  /** 备注 */
  private String remark;

  private LocalDateTime createdAt;

  /** 资金流水类型 */
  public enum FundFlowType {
    /** 初始入金 */
    INITIAL,
    /** 买入交易 */
    TRADE_BUY,
    /** 卖出交易 */
    TRADE_SELL,
    /** 手续费 */
    COMMISSION,
    /** 冻结 */
    FREEZE,
    /** 解冻 */
    UNFREEZE
  }
}
