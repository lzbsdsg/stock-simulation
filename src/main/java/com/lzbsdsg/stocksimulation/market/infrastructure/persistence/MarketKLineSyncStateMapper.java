package com.lzbsdsg.stocksimulation.market.infrastructure.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 历史日K同步状态 Mapper */
@Mapper
public interface MarketKLineSyncStateMapper {

  @Select(
      """
      SELECT stock_code, last_sync_date, last_bar_date, updated_at
      FROM t_market_kline_sync_state
      WHERE stock_code = #{stockCode}
      """)
  MarketKLineSyncStateDO selectByStockCode(@Param("stockCode") String stockCode);

  @Insert(
      """
      INSERT INTO t_market_kline_sync_state (
          stock_code,
          last_sync_date,
          last_bar_date,
          updated_at
      )
      VALUES (
          #{state.stockCode},
          #{state.lastSyncDate},
          #{state.lastBarDate},
          NOW()
      )
      ON CONFLICT (stock_code) DO UPDATE SET
          last_sync_date = EXCLUDED.last_sync_date,
          last_bar_date = EXCLUDED.last_bar_date,
          updated_at = NOW()
      """)
  int upsert(@Param("state") MarketKLineSyncStateDO state);
}
