package com.lzbsdsg.stocksimulation.portfolio.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** 资产快照 Mapper */
@Mapper
public interface AssetSnapshotMapper extends BaseMapper<AssetSnapshotDO> {}
