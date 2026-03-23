package com.lzbsdsg.stocksimulation.user.application;

import com.lzbsdsg.stocksimulation.common.exception.BizException;
import com.lzbsdsg.stocksimulation.common.result.ErrorCode;
import com.lzbsdsg.stocksimulation.user.domain.entity.Account;
import com.lzbsdsg.stocksimulation.user.domain.repository.AccountRepository;
import com.lzbsdsg.stocksimulation.user.domain.service.AccountDomainService;
import java.math.BigDecimal;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 账户应用服务
 *
 * <p>对外暴露账户操作接口，供 auth（注册创建账户）和 trade（冻结/解冻）模块调用。
 */
@Service
public class AccountApplicationService {

  private static final int MAX_OPTIMISTIC_RETRY = 3;

  private final AccountRepository accountRepository;
  private final AccountDomainService accountDomainService;

  public AccountApplicationService(AccountRepository accountRepository) {
    this.accountRepository = accountRepository;
    this.accountDomainService = new AccountDomainService();
  }

  /** 创建资金账户（注册时调用） */
  @Transactional
  public void createAccount(Long userId, BigDecimal initialBalance) {
    if (!accountDomainService.isValidInitialBalance(initialBalance)) {
      throw new BizException(ErrorCode.USER_INITIAL_BALANCE_INVALID);
    }
    if (accountRepository.findByUserId(userId).isPresent()) {
      return;
    }
    Account account = new Account();
    account.setUserId(userId);
    account.setInitialBalance(initialBalance);
    account.setAvailableBalance(initialBalance);
    account.setFrozenBalance(BigDecimal.ZERO);
    accountRepository.save(account);
  }

  /** 冻结资金（买入下单时调用） */
  @Transactional
  public void freezeBalance(Long userId, BigDecimal amount) {
    validatePositiveAmount(amount);
    executeWithOptimisticRetry(
        userId,
        account -> {
          try {
            accountDomainService.freeze(account, amount);
          } catch (IllegalStateException ex) {
            throw new BizException(ErrorCode.TRADE_ORDER_INSUFFICIENT_FUND, ex.getMessage());
          }
        });
  }

  /** 解冻资金（撤单/成交结算时调用） */
  @Transactional
  public void unfreezeBalance(Long userId, BigDecimal amount) {
    validatePositiveAmount(amount);
    executeWithOptimisticRetry(
        userId,
        account -> {
          try {
            accountDomainService.unfreeze(account, amount);
          } catch (IllegalStateException ex) {
            throw new BizException(ErrorCode.BAD_REQUEST, ex.getMessage());
          }
        });
  }

  /** 成交扣款（撮合时调用） */
  @Transactional
  public void deductFrozen(Long userId, BigDecimal frozenAmount, BigDecimal actualCost) {
    validatePositiveAmount(frozenAmount);
    validatePositiveAmount(actualCost);
    executeWithOptimisticRetry(
        userId,
        account -> {
          if (account.getFrozenBalance().compareTo(frozenAmount) < 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "解冻金额超过冻结金额");
          }

          account.setFrozenBalance(account.getFrozenBalance().subtract(frozenAmount));

          BigDecimal delta = frozenAmount.subtract(actualCost);
          if (delta.compareTo(BigDecimal.ZERO) >= 0) {
            account.setAvailableBalance(account.getAvailableBalance().add(delta));
            return;
          }

          BigDecimal extraCost = delta.abs();
          if (account.getAvailableBalance().compareTo(extraCost) < 0) {
            throw new BizException(ErrorCode.TRADE_ORDER_INSUFFICIENT_FUND, "可用资金不足");
          }
          account.setAvailableBalance(account.getAvailableBalance().subtract(extraCost));
        });
  }

  /** 卖出入账（撮合时调用） */
  @Transactional
  public void creditBalance(Long userId, BigDecimal amount) {
    validatePositiveAmount(amount);
    executeWithOptimisticRetry(
        userId,
        account -> account.setAvailableBalance(account.getAvailableBalance().add(amount)));
  }

  private void executeWithOptimisticRetry(Long userId, java.util.function.Consumer<Account> mutate) {
    for (int attempt = 1; attempt <= MAX_OPTIMISTIC_RETRY; attempt++) {
      Account account =
          accountRepository
              .findByUserIdForUpdate(userId)
              .orElseThrow(() -> new BizException(ErrorCode.USER_ACCOUNT_NOT_FOUND));

      mutate.accept(account);
      if (!account.isBalanceConsistent()) {
        throw new BizException(ErrorCode.BAD_REQUEST, "账户余额不一致");
      }

      if (accountRepository.updateWithVersion(account)) {
        return;
      }
    }
    throw new OptimisticLockingFailureException("账户更新发生乐观锁冲突，请重试");
  }

  private void validatePositiveAmount(BigDecimal amount) {
    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new BizException(ErrorCode.BAD_REQUEST, "金额必须大于0");
    }
  }
}
