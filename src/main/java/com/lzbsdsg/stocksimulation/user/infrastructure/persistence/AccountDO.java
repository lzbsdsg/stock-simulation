package com.lzbsdsg.stocksimulation.user.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.Instant;

/** 资金账户数据库映射对象 */
@TableName("t_user_account")
public class AccountDO {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long userId;
  private BigDecimal initialBalance;
  private BigDecimal availableBalance;
  private BigDecimal frozenBalance;

  @Version private Integer version;

  @TableField(fill = FieldFill.INSERT)
  private Instant createdAt;

  @TableField(fill = FieldFill.INSERT_UPDATE)
  private Instant updatedAt;

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
