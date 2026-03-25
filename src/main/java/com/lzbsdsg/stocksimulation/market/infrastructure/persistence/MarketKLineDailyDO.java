package com.lzbsdsg.stocksimulation.market.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Data;

/** 历史日K DO */
@Data
@TableName("t_market_kline_daily")
public class MarketKLineDailyDO {

  @TableId(type = IdType.AUTO)
  private Long id;

  private String stockCode;

  private LocalDate tradeDate;

  private BigDecimal openPrice;

  private BigDecimal closePrice;

  private BigDecimal highPrice;

  private BigDecimal lowPrice;

  private Long volume;

  private BigDecimal amount;

  private String source;

  private OffsetDateTime createdAt;

  private OffsetDateTime updatedAt;
}
