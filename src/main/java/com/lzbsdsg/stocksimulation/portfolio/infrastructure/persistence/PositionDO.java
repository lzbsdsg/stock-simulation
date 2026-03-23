package com.lzbsdsg.stocksimulation.portfolio.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/** 持仓 DO */
@Data
@TableName("t_portfolio_position")
public class PositionDO {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long userId;

  private String stockCode;

  private String stockName;

  private Integer totalQuantity;

  private Integer availableQuantity;

  private Integer frozenQuantity;

  private BigDecimal costPrice;

  private BigDecimal totalCost;

  private LocalDate frozenUntil;

  @Version private Integer version;

  private LocalDateTime createdAt;

  private LocalDateTime updatedAt;
}
