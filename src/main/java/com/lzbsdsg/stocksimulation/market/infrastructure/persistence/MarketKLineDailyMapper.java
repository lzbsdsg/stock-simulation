package com.lzbsdsg.stocksimulation.market.infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

/** 历史日K Mapper */
@Mapper
public interface MarketKLineDailyMapper {

  @Select(
      """
      <script>
      SELECT
          id,
          stock_code,
          trade_date,
          open_price,
          close_price,
          high_price,
          low_price,
          volume,
          amount,
          source,
          created_at,
          updated_at
      FROM t_market_kline_daily
      WHERE stock_code = #{stockCode}
        AND trade_date BETWEEN #{from} AND #{to}
      ORDER BY trade_date ASC
      </script>
      """)
  List<MarketKLineDailyDO> selectByStockCodeAndDateRange(
      @Param("stockCode") String stockCode, @Param("from") LocalDate from, @Param("to") LocalDate to);

  @Select(
      """
      SELECT MAX(trade_date)
      FROM t_market_kline_daily
      WHERE stock_code = #{stockCode}
      """)
  LocalDate selectLatestTradeDate(@Param("stockCode") String stockCode);

  @Select(
      """
      SELECT MIN(trade_date)
      FROM t_market_kline_daily
      WHERE stock_code = #{stockCode}
      """)
  LocalDate selectEarliestTradeDate(@Param("stockCode") String stockCode);

    @Select(
            """
            SELECT DISTINCT source
            FROM t_market_kline_daily
            WHERE stock_code = #{stockCode}
                AND trade_date BETWEEN #{from} AND #{to}
            """)
    List<String> selectDistinctSourcesInDateRange(
            @Param("stockCode") String stockCode, @Param("from") LocalDate from, @Param("to") LocalDate to);

  @Insert(
      """
      <script>
      INSERT INTO t_market_kline_daily (
          stock_code,
          trade_date,
          open_price,
          close_price,
          high_price,
          low_price,
          volume,
          amount,
          source,
          updated_at
      )
      VALUES
      <foreach collection="rows" item="row" separator=",">
          (
              #{row.stockCode},
              #{row.tradeDate},
              #{row.openPrice},
              #{row.closePrice},
              #{row.highPrice},
              #{row.lowPrice},
              #{row.volume},
              #{row.amount},
              #{row.source},
              NOW()
          )
      </foreach>
      ON CONFLICT (stock_code, trade_date) DO UPDATE SET
          open_price = EXCLUDED.open_price,
          close_price = EXCLUDED.close_price,
          high_price = EXCLUDED.high_price,
          low_price = EXCLUDED.low_price,
          volume = EXCLUDED.volume,
          amount = EXCLUDED.amount,
          source = EXCLUDED.source,
          updated_at = NOW()
      </script>
      """)
  int upsertBatch(@Param("rows") List<MarketKLineDailyDO> rows);

  @Delete(
      """
      DELETE FROM t_market_kline_daily
      WHERE stock_code = #{stockCode}
        AND trade_date < #{cutoffDateInclusive}
      """)
  int deleteOlderThan(
      @Param("stockCode") String stockCode, @Param("cutoffDateInclusive") LocalDate cutoffDateInclusive);
}
