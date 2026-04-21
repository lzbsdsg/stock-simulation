package com.lzbsdsg.stocksimulation.portfolio.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 持仓 Mapper */
@Mapper
public interface PositionMapper extends BaseMapper<PositionDO> {

  @Select(
      "SELECT * FROM t_portfolio_position WHERE user_id = #{userId} AND stock_code = #{stockCode} FOR UPDATE")
  PositionDO selectByUserIdAndStockCodeForUpdate(
      @Param("userId") Long userId, @Param("stockCode") String stockCode);

    @Select(
      "SELECT COALESCE(SUM(COALESCE(cost_price, 0) * COALESCE(total_quantity, 0)), 0) FROM t_portfolio_position WHERE user_id = #{userId}")
    BigDecimal sumCostMarketValueByUserId(@Param("userId") Long userId);

  @Update(
      """
      UPDATE t_portfolio_position
      SET frozen_until = #{frozenUntil}, updated_at = NOW()
      WHERE frozen_quantity > 0
        AND (frozen_until IS NULL OR frozen_until < #{frozenUntil})
        AND updated_at::date = #{tradeDate}
      """)
  int markTodayBoughtPositionsFrozenUntil(
      @Param("tradeDate") LocalDate tradeDate, @Param("frozenUntil") LocalDate frozenUntil);

  @Update(
      """
      UPDATE t_portfolio_position
      SET available_quantity = available_quantity + frozen_quantity,
          frozen_quantity = 0,
          frozen_until = NULL,
          updated_at = NOW()
      WHERE frozen_quantity > 0
        AND frozen_until IS NOT NULL
        AND frozen_until <= #{today}
      """)
  int unfreezeDuePositions(@Param("today") LocalDate today);
}
