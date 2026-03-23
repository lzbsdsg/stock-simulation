package com.lzbsdsg.stocksimulation.market.domain.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/** 行情快照（领域实体） */
@Data
public class QuoteSnapshot {

  /** 股票代码 */
  private String stockCode;

  /** 股票名称 */
  private String stockName;

  /** 当前价 */
  private BigDecimal currentPrice;

  /** 今开 */
  private BigDecimal openPrice;

  /** 昨收 */
  private BigDecimal closePrice;

  /** 最高 */
  private BigDecimal highPrice;

  /** 最低 */
  private BigDecimal lowPrice;

  /** 成交量（股） */
  private Long volume;

  /** 成交额（元） */
  private BigDecimal amount;

  /** 涨跌幅 % */
  private BigDecimal changePercent;

  /** 涨停价 */
  private BigDecimal upperLimitPrice;

  /** 跌停价 */
  private BigDecimal lowerLimitPrice;

  /** 行情时间 */
  private LocalDateTime timestamp;

  /** 判断是否涨停 */
  public boolean isUpperLimit() {
    return currentPrice != null
        && upperLimitPrice != null
        && currentPrice.compareTo(upperLimitPrice) >= 0;
  }

  /** 判断是否跌停 */
  public boolean isLowerLimit() {
    return currentPrice != null
        && lowerLimitPrice != null
        && currentPrice.compareTo(lowerLimitPrice) <= 0;
  }
}
