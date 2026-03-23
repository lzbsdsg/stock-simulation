package com.lzbsdsg.stocksimulation.watchlist.domain.entity;

import java.time.LocalDateTime;
import lombok.Data;

/** 自选股条目（领域实体） */
@Data
public class WatchlistItem {

  private Long id;

  private Long userId;

  private String stockCode;

  private String stockName;

  /** 排序序号 */
  private Integer sortOrder;

  private LocalDateTime createdAt;
}
