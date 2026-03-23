package com.lzbsdsg.stocksimulation.auth.domain.entity;

import java.time.Instant;

/** 用户领域实体（纯 POJO，不依赖任何框架） */
public class User {

  private Long id;
  private String email;
  private String passwordHash;
  private String nickname;
  private String avatarUrl;
  private String status; // ACTIVE / LOCKED / DISABLED
  private String role; // USER / ADMIN
  private int failedAttempts;
  private Instant lockedUntil;
  private Instant createdAt;
  private Instant updatedAt;

  public User() {}

  /** 判断账户是否被锁定 */
  public boolean isLocked() {
    return "LOCKED".equals(status) && lockedUntil != null && Instant.now().isBefore(lockedUntil);
  }

  /** 增加登录失败次数 */
  public void incrementFailedAttempts() {
    this.failedAttempts++;
  }

  /** 重置登录失败次数 */
  public void resetFailedAttempts() {
    this.failedAttempts = 0;
  }

  // ==================== Getters & Setters ====================

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public String getNickname() {
    return nickname;
  }

  public void setNickname(String nickname) {
    this.nickname = nickname;
  }

  public String getAvatarUrl() {
    return avatarUrl;
  }

  public void setAvatarUrl(String avatarUrl) {
    this.avatarUrl = avatarUrl;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getRole() {
    return role;
  }

  public void setRole(String role) {
    this.role = role;
  }

  public int getFailedAttempts() {
    return failedAttempts;
  }

  public void setFailedAttempts(int failedAttempts) {
    this.failedAttempts = failedAttempts;
  }

  public Instant getLockedUntil() {
    return lockedUntil;
  }

  public void setLockedUntil(Instant lockedUntil) {
    this.lockedUntil = lockedUntil;
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
