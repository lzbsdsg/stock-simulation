package com.lzbsdsg.stocksimulation.trade.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/** 成交记录 DO */
@Data
@TableName("t_trade_record")
public class TradeDO {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long orderId;

  private Long userId;

  private String stockCode;

  private String stockName;

  /** BUY / SELL */
  private String side;

  private BigDecimal tradePrice;

  private Integer tradeQuantity;

  private BigDecimal tradeAmount;

  private BigDecimal commission;

  private LocalDateTime tradedAt;

  private LocalDateTime createdAt;
}
