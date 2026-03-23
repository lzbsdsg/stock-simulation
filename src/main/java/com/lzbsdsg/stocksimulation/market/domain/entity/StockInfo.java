package com.lzbsdsg.stocksimulation.market.domain.entity;

import lombok.Data;

/** 股票基本信息（领域实体） */
@Data
public class StockInfo {

  private Long id;

  /** 股票代码，如 sh600519 / sz000001 */
  private String stockCode;

  /** 股票名称 */
  private String stockName;

  /** 市场: SH / SZ */
  private String market;

  /** 板块类型: MAIN=主板, GEM=创业板, STAR=科创板, ST=ST */
  private String boardType;

  /** 行业 */
  private String industry;

  /** 是否上市交易中 */
  private Boolean listed;
}
