package com.lzbsdsg.stocksimulation.portfolio.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/** 资金流水 DO */
@Data
@TableName("t_portfolio_fund_flow")
public class FundFlowDO {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long userId;

  /** INITIAL / TRADE_BUY / TRADE_SELL / COMMISSION / FREEZE / UNFREEZE */
  private String flowType;

  private BigDecimal amount;

  private BigDecimal balanceAfter;

  private Long orderId;

  private String remark;

  private LocalDateTime createdAt;
}
