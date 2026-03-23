package com.lzbsdsg.stocksimulation.market.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** 股票信息 Mapper */
@Mapper
public interface StockInfoMapper extends BaseMapper<StockInfoDO> {}
