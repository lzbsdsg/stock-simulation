package com.lzbsdsg.stocksimulation.user.application;

import java.math.BigDecimal;
import org.springframework.stereotype.Service;

/**
 * 账户应用服务
 *
 * <p>对外暴露账户操作接口，供 auth（注册创建账户）和 trade（冻结/解冻）模块调用。
 */
@Service
public class AccountApplicationService {

  // TODO: 注入 AccountRepository, AccountDomainService

  /** 创建资金账户（注册时调用） */
  public void createAccount(Long userId, BigDecimal initialBalance) {
    // TODO: 创建 Account 实体 → 存储
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
