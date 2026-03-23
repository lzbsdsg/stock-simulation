package com.lzbsdsg.stocksimulation.user.application;

import com.lzbsdsg.stocksimulation.common.exception.BizException;
import com.lzbsdsg.stocksimulation.common.result.ErrorCode;
import com.lzbsdsg.stocksimulation.user.domain.entity.Account;
import com.lzbsdsg.stocksimulation.user.domain.repository.AccountRepository;
import com.lzbsdsg.stocksimulation.user.domain.service.AccountDomainService;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 账户应用服务
 *
 * <p>对外暴露账户操作接口，供 auth（注册创建账户）和 trade（冻结/解冻）模块调用。
 */
@Service
public class AccountApplicationService {

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
  public void freezeBalance(Long userId, BigDecimal amount) {
    // TODO: SELECT FOR UPDATE → 校验可用余额 → 扣减可用 + 增加冻结
  }

  /** 解冻资金（撤单/成交结算时调用） */
  public void unfreezeBalance(Long userId, BigDecimal amount) {
    // TODO: 校验冻结余额 → 解冻
  }

  /** 成交扣款（撮合时调用） */
  public void deductFrozen(Long userId, BigDecimal frozenAmount, BigDecimal actualCost) {
    // TODO: 解冻 frozenAmount → 扣减 actualCost → 多退少补
  }

  /** 卖出入账（撮合时调用） */
  public void creditBalance(Long userId, BigDecimal amount) {
    // TODO: 增加可用余额
  }
}
