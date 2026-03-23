package com.lzbsdsg.stocksimulation.portfolio.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/** 资产快照 DO */
@Data
@TableName("t_portfolio_asset_snapshot")
public class AssetSnapshotDO {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long userId;

  private LocalDate snapshotDate;

  private BigDecimal totalAssets;

  private BigDecimal availableBalance;

  private BigDecimal marketValue;

  private BigDecimal dailyProfit;

  private BigDecimal cumulativeProfitRate;

  private LocalDateTime createdAt;
}
