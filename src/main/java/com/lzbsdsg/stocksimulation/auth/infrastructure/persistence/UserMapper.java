package com.lzbsdsg.stocksimulation.auth.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** 用户 MyBatis-Plus Mapper */
@Mapper
public interface UserMapper extends BaseMapper<UserDO> {}
