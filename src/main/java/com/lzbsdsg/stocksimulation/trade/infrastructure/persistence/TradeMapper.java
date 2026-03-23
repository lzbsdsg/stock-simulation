package com.lzbsdsg.stocksimulation.trade.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** 成交记录 Mapper */
@Mapper
public interface TradeMapper extends BaseMapper<TradeDO> {}
