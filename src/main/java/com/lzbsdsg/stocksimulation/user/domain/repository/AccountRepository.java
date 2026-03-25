package com.lzbsdsg.stocksimulation.user.domain.repository;

import com.lzbsdsg.stocksimulation.user.domain.entity.Account;
import java.util.List;
import java.util.Optional;

/** 资金账户仓储接口 */
public interface AccountRepository {

  Optional<Account> findByUserId(Long userId);

  /** 悲观锁查询（SELECT ... FOR UPDATE） */
  Optional<Account> findByUserIdForUpdate(Long userId);

  Account save(Account account);

  /**
   * 带乐观锁更新（WHERE version = ?）
   *
   * @return 是否更新成功
   */
  boolean updateWithVersion(Account account);

  /** 按 userId 游标分页查询账户用户ID（用于分批任务）。 */
  List<Long> findUserIdsAfter(Long lastUserId, int limit);
}
