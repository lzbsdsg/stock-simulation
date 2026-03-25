package com.lzbsdsg.stocksimulation.market.domain.entity;

import java.time.LocalDate;
import lombok.Data;

/** 历史K线同步状态（按股票） */
@Data
public class MarketKLineSyncState {

  private String stockCode;

  private LocalDate lastSyncDate;

  private LocalDate lastBarDate;
}
