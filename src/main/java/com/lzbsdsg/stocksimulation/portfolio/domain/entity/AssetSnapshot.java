package com.lzbsdsg.stocksimulation.portfolio.domain.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 资产快照（领域实体）
 *
 * <p>每日收盘后记录用户资产快照，用于收益曲线展示。
 */
@Data
public class AssetSnapshot {

  private Long id;

  private Long userId;

  /** 快照日期 */
  private LocalDate snapshotDate;

  /** 总资产 */
  private BigDecimal totalAssets;

  /** 可用资金 */
  private BigDecimal availableBalance;

  /** 持仓市值 */
  private BigDecimal marketValue;

  /** 当日收益 */
  private BigDecimal dailyProfit;

  /** 累计收益率 % */
  private BigDecimal cumulativeProfitRate;

  private LocalDateTime createdAt;
}
