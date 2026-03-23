package com.lzbsdsg.stocksimulation.user.domain.service;

import com.lzbsdsg.stocksimulation.user.domain.entity.Account;
import java.math.BigDecimal;

/** 账户领域服务（纯业务逻辑） */
public class AccountDomainService {

  /**
   * 冻结资金
   *
   * @throws IllegalStateException 余额不足
   */
  public void freeze(Account account, BigDecimal amount) {
    if (account.getAvailableBalance().compareTo(amount) < 0) {
      throw new IllegalStateException("可用资金不足");
    }
    account.setAvailableBalance(account.getAvailableBalance().subtract(amount));
    account.setFrozenBalance(account.getFrozenBalance().add(amount));
  }

  /**
   * 解冻资金
   *
   * @throws IllegalStateException 解冻金额超过冻结金额
   */
  public void unfreeze(Account account, BigDecimal amount) {
    if (account.getFrozenBalance().compareTo(amount) < 0) {
      throw new IllegalStateException("解冻金额超过冻结金额");
    }
    account.setFrozenBalance(account.getFrozenBalance().subtract(amount));
    account.setAvailableBalance(account.getAvailableBalance().add(amount));
  }

  /** 校验初始资金范围 */
  public boolean isValidInitialBalance(BigDecimal initialBalance) {
    BigDecimal min = new BigDecimal("10000");
    BigDecimal max = new BigDecimal("1000000");
    return initialBalance.compareTo(min) >= 0 && initialBalance.compareTo(max) <= 0;
  }
}
