package com.lzbsdsg.stocksimulation.portfolio.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 持仓 Mapper */
@Mapper
public interface PositionMapper extends BaseMapper<PositionDO> {

  @Select(
      "SELECT * FROM t_portfolio_position WHERE user_id = #{userId} AND stock_code = #{stockCode} FOR UPDATE")
  PositionDO selectByUserIdAndStockCodeForUpdate(
      @Param("userId") Long userId, @Param("stockCode") String stockCode);
}
