package com.lzbsdsg.stocksimulation.market.domain.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

/** K线数据点（领域实体） */
@Data
public class KLinePoint {

  /** 日期 */
  private LocalDate date;

  /** 开盘价 */
  private BigDecimal open;

  /** 收盘价 */
  private BigDecimal close;

  /** 最高价 */
  private BigDecimal high;

  /** 最低价 */
  private BigDecimal low;

  /** 成交量（股） */
  private Long volume;

  /** 成交额（元） */
  private BigDecimal amount;
}
