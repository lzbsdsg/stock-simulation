package com.lzbsdsg.stocksimulation.trade.domain.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/** 成交记录（领域实体） */
@Data
public class Trade {

  private Long id;

  /** 关联订单ID */
  private Long orderId;

  /** 用户ID */
  private Long userId;

  /** 股票代码 */
  private String stockCode;

  /** 股票名称 */
  private String stockName;

  /** 买卖方向 */
  private OrderSide side;

  /** 成交价格 */
  private BigDecimal tradePrice;

  /** 成交数量 */
  private Integer tradeQuantity;

  /** 成交金额 */
  private BigDecimal tradeAmount;

  /** 手续费 */
  private BigDecimal commission;

  /** 成交时间 */
  private LocalDateTime tradedAt;
}
