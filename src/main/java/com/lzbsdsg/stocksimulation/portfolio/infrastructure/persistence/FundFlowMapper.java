package com.lzbsdsg.stocksimulation.portfolio.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** 资金流水 Mapper */
@Mapper
public interface FundFlowMapper extends BaseMapper<FundFlowDO> {}
