package com.lzbsdsg.stocksimulation.user.domain.entity;

import java.math.BigDecimal;
import java.time.Instant;

/** 资金账户领域实体（纯 POJO） */
public class Account {

  private Long id;
  private Long userId;
  private BigDecimal initialBalance;
  private BigDecimal availableBalance;
  private BigDecimal frozenBalance;
  private Integer version;
  private Instant createdAt;
  private Instant updatedAt;

  public Account() {
    this.frozenBalance = BigDecimal.ZERO;
    this.version = 0;
  }

  /** 余额一致性校验：可用/冻结余额均不可为负。 */
  public boolean isBalanceConsistent() {
    return availableBalance.compareTo(BigDecimal.ZERO) >= 0
        && frozenBalance.compareTo(BigDecimal.ZERO) >= 0;
  }

  // ==================== Getters & Setters ====================

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public BigDecimal getInitialBalance() {
    return initialBalance;
  }

  public void setInitialBalance(BigDecimal initialBalance) {
    this.initialBalance = initialBalance;
  }

  public BigDecimal getAvailableBalance() {
    return availableBalance;
  }

  public void setAvailableBalance(BigDecimal availableBalance) {
    this.availableBalance = availableBalance;
  }

  public BigDecimal getFrozenBalance() {
    return frozenBalance;
  }

  public void setFrozenBalance(BigDecimal frozenBalance) {
    this.frozenBalance = frozenBalance;
  }

  public Integer getVersion() {
    return version;
  }

  public void setVersion(Integer version) {
    this.version = version;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
