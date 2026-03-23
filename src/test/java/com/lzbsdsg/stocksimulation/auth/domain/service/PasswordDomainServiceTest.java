package com.lzbsdsg.stocksimulation.auth.domain.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lzbsdsg.stocksimulation.auth.domain.entity.User;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 密码领域服务单元测试。 */
public class PasswordDomainServiceTest {

  private final PasswordDomainService passwordDomainService = new PasswordDomainService();

  @Test
  void should_accept_strong_password() {
    assertTrue(passwordDomainService.isPasswordStrong("Strong123"));
  }

  @Test
  void should_reject_password_without_uppercase() {
    assertFalse(passwordDomainService.isPasswordStrong("strong123"));
  }

  @Test
  void should_reject_password_without_lowercase() {
    assertFalse(passwordDomainService.isPasswordStrong("STRONG123"));
  }

  @Test
  void should_reject_password_without_digit() {
    assertFalse(passwordDomainService.isPasswordStrong("StrongPwd"));
  }

  @Test
  void should_lock_account_after_five_failures() {
    assertTrue(passwordDomainService.shouldLockAccount(5));
    assertFalse(passwordDomainService.shouldLockAccount(4));
  }

  @Test
  void should_detect_account_locked_state() {
    User user = new User();
    user.setStatus("LOCKED");
    user.setLockedUntil(Instant.now().plusSeconds(1800));
    assertTrue(passwordDomainService.isAccountLocked(user));
  }
}
