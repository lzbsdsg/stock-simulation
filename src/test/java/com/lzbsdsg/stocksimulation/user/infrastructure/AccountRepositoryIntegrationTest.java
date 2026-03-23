package com.lzbsdsg.stocksimulation.user.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lzbsdsg.stocksimulation.user.domain.entity.Account;
import com.lzbsdsg.stocksimulation.user.infrastructure.persistence.AccountDO;
import com.lzbsdsg.stocksimulation.user.infrastructure.persistence.AccountMapper;
import com.lzbsdsg.stocksimulation.user.infrastructure.persistence.AccountRepositoryImpl;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** 账户 Repository 测试。 */
public class AccountRepositoryIntegrationTest {

  private AccountMapper accountMapper;
  private AccountRepositoryImpl accountRepository;

  @BeforeEach
  void setUp() {
    accountMapper = Mockito.mock(AccountMapper.class);
    accountRepository = new AccountRepositoryImpl(accountMapper);
  }

  @Test
  void should_save_and_find_account() {
    when(accountMapper.insert(any(AccountDO.class)))
        .thenAnswer(
            invocation -> {
              AccountDO accountDO = invocation.getArgument(0);
              accountDO.setId(1L);
              return 1;
            });

    Account account = newAccount(10L, "100000", "100000", "0", 0);
    Account saved = accountRepository.save(account);

    assertEquals(1L, saved.getId());

    AccountDO stored = new AccountDO();
    stored.setId(1L);
    stored.setUserId(10L);
    stored.setInitialBalance(new BigDecimal("100000"));
    stored.setAvailableBalance(new BigDecimal("100000"));
    stored.setFrozenBalance(BigDecimal.ZERO);
    stored.setVersion(0);
    when(accountMapper.selectOne(any())).thenReturn(stored);

    Optional<Account> queried = accountRepository.findByUserId(10L);
    assertTrue(queried.isPresent());
    assertEquals(10L, queried.get().getUserId());
  }

  @Test
  void should_update_with_optimistic_lock() {
    when(accountMapper.updateById(any(AccountDO.class))).thenReturn(1);

    boolean updated =
        accountRepository.updateWithVersion(newAccount(10L, "100000", "80000", "20000", 3));

    assertTrue(updated);
    verify(accountMapper).updateById(any(AccountDO.class));
  }

  @Test
  void should_return_false_when_version_conflict() {
    when(accountMapper.updateById(any(AccountDO.class))).thenReturn(0);

    boolean updated =
        accountRepository.updateWithVersion(newAccount(10L, "100000", "80000", "20000", 3));

    assertFalse(updated);
  }

  @Test
  void should_query_by_user_id_for_update() {
    AccountDO locked = new AccountDO();
    locked.setId(9L);
    locked.setUserId(88L);
    locked.setInitialBalance(new BigDecimal("10000"));
    locked.setAvailableBalance(new BigDecimal("9000"));
    locked.setFrozenBalance(new BigDecimal("1000"));
    locked.setVersion(1);
    when(accountMapper.selectByUserIdForUpdate(88L)).thenReturn(locked);

    Optional<Account> account = accountRepository.findByUserIdForUpdate(88L);

    assertTrue(account.isPresent());
    assertEquals(88L, account.get().getUserId());
    assertEquals(new BigDecimal("9000"), account.get().getAvailableBalance());
  }

  private Account newAccount(
      Long userId, String initial, String available, String frozen, Integer version) {
    Account account = new Account();
    account.setUserId(userId);
    account.setInitialBalance(new BigDecimal(initial));
    account.setAvailableBalance(new BigDecimal(available));
    account.setFrozenBalance(new BigDecimal(frozen));
    account.setVersion(version);
    return account;
  }
}
