package com.lzbsdsg.stocksimulation.market.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Data;

/** 历史日K同步状态 DO */
@Data
@TableName("t_market_kline_sync_state")
public class MarketKLineSyncStateDO {

  @TableId private String stockCode;

  private LocalDate lastSyncDate;

  private LocalDate lastBarDate;

  private OffsetDateTime updatedAt;
}
