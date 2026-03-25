package com.lzbsdsg.stocksimulation.user.infrastructure.persistence;

import com.lzbsdsg.stocksimulation.user.domain.entity.Account;
import com.lzbsdsg.stocksimulation.user.domain.repository.AccountRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 账户仓储实现 */
@Repository
@RequiredArgsConstructor
public class AccountRepositoryImpl implements AccountRepository {

  private final AccountMapper accountMapper;

  @Override
  public Optional<Account> findByUserId(Long userId) {
    AccountDO accountDO =
        accountMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AccountDO>()
                .eq(AccountDO::getUserId, userId));
    return Optional.ofNullable(accountDO).map(this::toDomain);
  }

  @Override
  public Optional<Account> findByUserIdForUpdate(Long userId) {
    AccountDO accountDO = accountMapper.selectByUserIdForUpdate(userId);
    return Optional.ofNullable(accountDO).map(this::toDomain);
  }

  @Override
  public Account save(Account account) {
    AccountDO accountDO = toDO(account);
    accountMapper.insert(accountDO);
    account.setId(accountDO.getId());
    return account;
  }

  @Override
  public boolean updateWithVersion(Account account) {
    AccountDO accountDO = toDO(account);
    // MyBatis-Plus 乐观锁插件会自动处理 version 字段
    int rows = accountMapper.updateById(accountDO);
    return rows > 0;
  }

  @Override
  public List<Long> findUserIdsAfter(Long lastUserId, int limit) {
    long safeLastUserId = lastUserId == null ? 0L : lastUserId;
    int safeLimit = limit <= 0 ? 500 : limit;
    return accountMapper.selectUserIdsAfter(safeLastUserId, safeLimit);
  }

  // ---- Converter ----

  private Account toDomain(AccountDO d) {
    Account a = new Account();
    a.setId(d.getId());
    a.setUserId(d.getUserId());
    a.setInitialBalance(d.getInitialBalance());
    a.setAvailableBalance(d.getAvailableBalance());
    a.setFrozenBalance(d.getFrozenBalance());
    a.setVersion(d.getVersion());
    return a;
  }

  private AccountDO toDO(Account a) {
    AccountDO d = new AccountDO();
    d.setId(a.getId());
    d.setUserId(a.getUserId());
    d.setInitialBalance(a.getInitialBalance());
    d.setAvailableBalance(a.getAvailableBalance());
    d.setFrozenBalance(a.getFrozenBalance());
    d.setVersion(a.getVersion());
    return d;
  }
}
