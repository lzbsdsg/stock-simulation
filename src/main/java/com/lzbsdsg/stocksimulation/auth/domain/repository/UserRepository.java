package com.lzbsdsg.stocksimulation.auth.domain.repository;

import com.lzbsdsg.stocksimulation.auth.domain.entity.User;
import java.util.Optional;

/** 用户仓储接口（定义在 domain 层，实现在 infrastructure 层） */
public interface UserRepository {

  Optional<User> findByEmail(String email);

  Optional<User> findById(Long id);

  User save(User user);

  void updateFailedAttempts(Long userId, int failedAttempts, java.time.Instant lockedUntil);

  void updatePassword(Long userId, String newPasswordHash);

  boolean existsByEmail(String email);
}
