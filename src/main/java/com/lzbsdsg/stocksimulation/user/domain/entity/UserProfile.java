package com.lzbsdsg.stocksimulation.user.domain.entity;

/** 用户资料领域实体 */
public class UserProfile {

  private Long id;
  private Long userId;
  private String nickname;
  private String avatarUrl;

  public UserProfile() {}

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
}
