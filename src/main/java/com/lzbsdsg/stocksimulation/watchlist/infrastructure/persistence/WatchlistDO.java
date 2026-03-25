package com.lzbsdsg.stocksimulation.watchlist.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.Data;

/** 自选股 DO */
@Data
@TableName("t_watchlist_item")
public class WatchlistDO {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long userId;

  private String stockCode;

  private String stockName;

  private Integer sortOrder;

  private OffsetDateTime createdAt;
}
