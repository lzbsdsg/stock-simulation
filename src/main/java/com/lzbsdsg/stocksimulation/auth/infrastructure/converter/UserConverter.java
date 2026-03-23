package com.lzbsdsg.stocksimulation.auth.infrastructure.converter;

import com.lzbsdsg.stocksimulation.auth.domain.entity.User;
import com.lzbsdsg.stocksimulation.auth.infrastructure.persistence.UserDO;
import org.springframework.stereotype.Component;

/**
 * User ↔ UserDO 转换器
 *
 * <p>TODO: 后续可替换为 MapStruct @Mapper 实现
 */
@Component
public class UserConverter {

  public User toDomain(UserDO userDO) {
    if (userDO == null) return null;
    User user = new User();
    user.setId(userDO.getId());
    user.setEmail(userDO.getEmail());
    user.setPasswordHash(userDO.getPasswordHash());
    user.setNickname(userDO.getNickname());
    user.setAvatarUrl(userDO.getAvatarUrl());
    user.setStatus(userDO.getStatus());
    user.setRole(userDO.getRole());
    user.setFailedAttempts(userDO.getFailedAttempts() != null ? userDO.getFailedAttempts() : 0);
    user.setLockedUntil(userDO.getLockedUntil());
    user.setCreatedAt(userDO.getCreatedAt());
    user.setUpdatedAt(userDO.getUpdatedAt());
    return user;
  }

  public UserDO toDO(User user) {
    if (user == null) return null;
    UserDO userDO = new UserDO();
    userDO.setId(user.getId());
    userDO.setEmail(user.getEmail());
    userDO.setPasswordHash(user.getPasswordHash());
    userDO.setNickname(user.getNickname());
    userDO.setAvatarUrl(user.getAvatarUrl());
    userDO.setStatus(user.getStatus());
    userDO.setRole(user.getRole());
    userDO.setFailedAttempts(user.getFailedAttempts());
    userDO.setLockedUntil(user.getLockedUntil());
    return userDO;
  }
}
