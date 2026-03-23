package com.lzbsdsg.stocksimulation.auth.domain.service;

import com.lzbsdsg.stocksimulation.auth.domain.entity.User;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;

/** 密码领域服务（纯逻辑，不依赖框架） */
public class PasswordDomainService {

  /** 密码最低长度 */
  private static final int MIN_LENGTH = 8;

  /** 最大连续失败次数 */
  private static final int MAX_FAILED_ATTEMPTS = 5;

  /** 锁定时长（分钟） */
  private static final long LOCK_DURATION_MINUTES = 30;

  /** 密码强度正则：至少包含一个大写、一个小写、一个数字 */
  private static final Pattern STRONG_PASSWORD =
      Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$");

  /** 校验密码强度 */
  public boolean isPasswordStrong(String password) {
    return password != null && STRONG_PASSWORD.matcher(password).matches();
  }

  /** 判断是否需要锁定账户 */
  public boolean shouldLockAccount(int failedAttempts) {
    return failedAttempts >= MAX_FAILED_ATTEMPTS;
  }

  /** 计算锁定截止时间 */
  public Instant calculateLockUntil() {
    return Instant.now().plus(Duration.ofMinutes(LOCK_DURATION_MINUTES));
  }

  /** 判断账户是否仍在锁定期内 */
  public boolean isAccountLocked(User user) {
    return user.isLocked();
  }

  public int getMaxFailedAttempts() {
    return MAX_FAILED_ATTEMPTS;
  }
}
