package com.lzbsdsg.stocksimulation.user.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** 资金账户 MyBatis-Plus Mapper */
@Mapper
public interface AccountMapper extends BaseMapper<AccountDO> {

  /** 悲观锁查询 */
  @Select("SELECT * FROM t_user_account WHERE user_id = #{userId} FOR UPDATE")
  AccountDO selectByUserIdForUpdate(Long userId);
}
