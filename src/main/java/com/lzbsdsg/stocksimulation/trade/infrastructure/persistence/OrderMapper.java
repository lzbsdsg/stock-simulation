package com.lzbsdsg.stocksimulation.trade.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** 委托订单 Mapper */
@Mapper
public interface OrderMapper extends BaseMapper<OrderDO> {}
