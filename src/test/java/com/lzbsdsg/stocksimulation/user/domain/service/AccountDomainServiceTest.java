package com.lzbsdsg.stocksimulation.user.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lzbsdsg.stocksimulation.user.domain.entity.Account;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** 账户领域服务单元测试。 */
public class AccountDomainServiceTest {

  private final AccountDomainService accountDomainService = new AccountDomainService();

  @Test
  void should_freeze_amount_when_sufficient_balance() {
    Account account = newAccount("10000", "10000", "0");

    accountDomainService.freeze(account, new BigDecimal("1000"));

    assertEquals(new BigDecimal("9000"), account.getAvailableBalance());
    assertEquals(new BigDecimal("1000"), account.getFrozenBalance());
    assertTrue(account.isBalanceConsistent());
  }

  @Test
  void should_throw_when_balance_not_enough_for_freeze() {
    Account account = newAccount("10000", "100", "0");

    assertThrows(
        IllegalStateException.class,
        () -> accountDomainService.freeze(account, new BigDecimal("101")));
  }

  @Test
  void should_unfreeze_amount_and_restore_available() {
    Account account = newAccount("10000", "7000", "3000");

    accountDomainService.unfreeze(account, new BigDecimal("1200"));

    assertEquals(new BigDecimal("8200"), account.getAvailableBalance());
    assertEquals(new BigDecimal("1800"), account.getFrozenBalance());
    assertTrue(account.isBalanceConsistent());
  }

  @Test
  void should_throw_when_unfreeze_exceeds_frozen() {
    Account account = newAccount("10000", "9000", "1000");

    assertThrows(
        IllegalStateException.class,
        () -> accountDomainService.unfreeze(account, new BigDecimal("1001")));
  }

  @Test
  void should_validate_balance_equation_after_every_operation() {
    Account account = newAccount("50000", "50000", "0");

    accountDomainService.freeze(account, new BigDecimal("12345"));
    accountDomainService.unfreeze(account, new BigDecimal("2345"));

    assertEquals(new BigDecimal("40000"), account.getAvailableBalance());
    assertEquals(new BigDecimal("10000"), account.getFrozenBalance());
    assertTrue(account.isBalanceConsistent());
  }

  @Test
  void should_create_account_with_valid_initial_balance_range() {
    assertTrue(accountDomainService.isValidInitialBalance(new BigDecimal("10000")));
    assertTrue(accountDomainService.isValidInitialBalance(new BigDecimal("1000000")));
    assertTrue(accountDomainService.isValidInitialBalance(new BigDecimal("888888")));
  }

  @Test
  void should_reject_initial_balance_below_minimum() {
    assertFalse(accountDomainService.isValidInitialBalance(new BigDecimal("9999")));
  }

  @Test
  void should_reject_initial_balance_above_maximum() {
    assertFalse(accountDomainService.isValidInitialBalance(new BigDecimal("1000000.01")));
  }

  private Account newAccount(String initial, String available, String frozen) {
    Account account = new Account();
    account.setInitialBalance(new BigDecimal(initial));
    account.setAvailableBalance(new BigDecimal(available));
    account.setFrozenBalance(new BigDecimal(frozen));
    return account;
  }
}
