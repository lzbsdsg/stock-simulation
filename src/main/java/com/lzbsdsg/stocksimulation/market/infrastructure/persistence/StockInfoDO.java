package com.lzbsdsg.stocksimulation.market.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 股票信息 DO */
@Data
@TableName("t_market_stock_info")
public class StockInfoDO {

  @TableId(type = IdType.AUTO)
  private Long id;

  private String stockCode;

  private String stockName;

  private String market;

  private String boardType;

  private String industry;

  private Boolean listed;

  private LocalDateTime createdAt;

  private LocalDateTime updatedAt;
}
