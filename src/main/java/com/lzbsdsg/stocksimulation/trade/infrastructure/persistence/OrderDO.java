package com.lzbsdsg.stocksimulation.trade.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/** 委托订单 DO */
@Data
@TableName("t_trade_order")
public class OrderDO {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long userId;

  private String clientOrderId;

  private String stockCode;

  private String stockName;

  /** BUY / SELL */
  private String side;

  /** LIMIT / MARKET */
  private String orderType;

  /** PENDING / PARTIAL_FILLED / FILLED / CANCELLED / REJECTED / EXPIRED */
  private String status;

  private BigDecimal price;

  private Integer quantity;

  private Integer filledQuantity;

  private BigDecimal filledAmount;

  private BigDecimal commission;

  private BigDecimal frozenAmount;

  @Version private Integer version;

  private LocalDateTime createdAt;

  private LocalDateTime updatedAt;
}
