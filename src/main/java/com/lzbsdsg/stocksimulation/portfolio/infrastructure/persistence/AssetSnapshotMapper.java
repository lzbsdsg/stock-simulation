package com.lzbsdsg.stocksimulation.portfolio.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 资产快照 Mapper */
@Mapper
public interface AssetSnapshotMapper extends BaseMapper<AssetSnapshotDO> {

  @Select(
      """
      SELECT *
      FROM t_portfolio_asset_snapshot
      WHERE user_id = #{userId}
        AND snapshot_date < #{snapshotDate}
      ORDER BY snapshot_date DESC
      LIMIT 1
      """)
  AssetSnapshotDO selectLatestBefore(
      @Param("userId") Long userId, @Param("snapshotDate") java.time.LocalDate snapshotDate);
}
