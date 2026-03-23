package com.lzbsdsg.stocksimulation.watchlist.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** 自选股 Mapper */
@Mapper
public interface WatchlistMapper extends BaseMapper<WatchlistDO> {}
