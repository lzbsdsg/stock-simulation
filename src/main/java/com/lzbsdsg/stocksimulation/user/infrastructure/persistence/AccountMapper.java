package com.lzbsdsg.stocksimulation.user.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 资金账户 MyBatis-Plus Mapper */
@Mapper
public interface AccountMapper extends BaseMapper<AccountDO> {

  /** 悲观锁查询 */
  @Select("SELECT * FROM t_user_account WHERE user_id = #{userId} FOR UPDATE")
  AccountDO selectByUserIdForUpdate(Long userId);

  /** 按 user_id 游标分页查询账户用户ID。 */
  @Select(
      """
      SELECT user_id
      FROM t_user_account
      WHERE user_id > #{lastUserId}
      ORDER BY user_id ASC
      LIMIT #{limit}
      """)
  List<Long> selectUserIdsAfter(
      @Param("lastUserId") Long lastUserId, @Param("limit") int limit);
}
