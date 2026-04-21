package com.lzbsdsg.stocksimulation.auth.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lzbsdsg.stocksimulation.auth.application.command.LoginCommand;
import com.lzbsdsg.stocksimulation.auth.application.command.RefreshTokenCommand;
import com.lzbsdsg.stocksimulation.auth.application.command.RegisterCommand;
import com.lzbsdsg.stocksimulation.auth.application.command.ResetPasswordCommand;
import com.lzbsdsg.stocksimulation.auth.application.command.SendOtpCommand;
import com.lzbsdsg.stocksimulation.auth.application.dto.TokenDTO;
import com.lzbsdsg.stocksimulation.auth.domain.entity.User;
import com.lzbsdsg.stocksimulation.auth.domain.repository.UserRepository;
import com.lzbsdsg.stocksimulation.auth.infrastructure.gateway.EmailGateway;
import com.lzbsdsg.stocksimulation.auth.infrastructure.gateway.JwtTokenProvider;
import com.lzbsdsg.stocksimulation.auth.infrastructure.gateway.OtpRedisGateway;
import com.lzbsdsg.stocksimulation.common.exception.BizException;
import com.lzbsdsg.stocksimulation.user.application.AccountApplicationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

public class AuthApplicationServiceTest {

  private UserRepository userRepository;
  private AccountApplicationService accountApplicationService;
  private OtpRedisGateway otpRedisGateway;
  private JwtTokenProvider jwtTokenProvider;
  private EmailGateway emailGateway;
  private PasswordEncoder passwordEncoder;
  private AuthApplicationService authApplicationService;

  @BeforeEach
  void setUp() {
    userRepository = Mockito.mock(UserRepository.class);
    accountApplicationService = Mockito.mock(AccountApplicationService.class);
    otpRedisGateway = Mockito.mock(OtpRedisGateway.class);
    jwtTokenProvider = Mockito.mock(JwtTokenProvider.class);
    emailGateway = Mockito.mock(EmailGateway.class);
    passwordEncoder = new BCryptPasswordEncoder(4);

    authApplicationService =
        new AuthApplicationService(
            userRepository,
            accountApplicationService,
            otpRedisGateway,
            jwtTokenProvider,
            emailGateway,
            passwordEncoder,
            new ConcurrentMapCacheManager("loginLock"));
  }

  @Test
  void should_send_otp_when_not_rate_limited() {
    when(otpRedisGateway.isRateLimited("u@test.com")).thenReturn(false);
    when(otpRedisGateway.isIpLimited(any())).thenReturn(false);

    authApplicationService.sendOtp(new SendOtpCommand("u@test.com"));

    verify(otpRedisGateway).storeOtp(eq("u@test.com"), any());
    verify(otpRedisGateway).markSent("u@test.com");
    verify(emailGateway).sendOtpEmail(eq("u@test.com"), any());
  }

  @Test
  void should_register_new_user_with_valid_otp() {
    RegisterCommand command =
        new RegisterCommand("u@test.com", "123456", "Strong123", "tester", new BigDecimal("10000"));
    when(otpRedisGateway.getStoredOtpHash("u@test.com"))
        .thenReturn(passwordEncoder.encode("123456"));
    when(userRepository.existsByEmail("u@test.com")).thenReturn(false);
    when(userRepository.save(any()))
        .thenAnswer(
            invocation -> {
              User u = invocation.getArgument(0);
              u.setId(1L);
              return u;
            });
    when(jwtTokenProvider.generateAccessToken(eq(1L), eq("u@test.com"), eq("USER")))
        .thenReturn("access");
    when(jwtTokenProvider.generateRefreshToken(1L)).thenReturn("refresh");
    when(jwtTokenProvider.getAccessTokenExpiration()).thenReturn(1800000L);

    TokenDTO token = authApplicationService.register(command);

    assertNotNull(token);
    assertEquals("access", token.accessToken());
    verify(accountApplicationService).createAccount(1L, new BigDecimal("10000"));
    verify(otpRedisGateway).deleteOtp("u@test.com");
  }

  @Test
  void should_reject_registration_with_duplicate_email() {
    RegisterCommand command =
        new RegisterCommand("u@test.com", "123456", "Strong123", "tester", new BigDecimal("10000"));
    when(otpRedisGateway.getStoredOtpHash("u@test.com"))
        .thenReturn(passwordEncoder.encode("123456"));
    when(userRepository.existsByEmail("u@test.com")).thenReturn(true);

    assertThrows(BizException.class, () -> authApplicationService.register(command));
    verify(accountApplicationService, never()).createAccount(any(), any());
  }

  @Test
  void should_reject_weak_password_without_consuming_otp_on_register() {
    RegisterCommand command =
        new RegisterCommand("u@test.com", "123456", "weak", "tester", new BigDecimal("10000"));
    when(userRepository.existsByEmail("u@test.com")).thenReturn(false);

    assertThrows(BizException.class, () -> authApplicationService.register(command));
    verify(otpRedisGateway, never()).getStoredOtpHash(any());
    verify(otpRedisGateway, never()).deleteOtp(any());
  }

  @Test
  void should_login_and_return_tokens() {
    User user = buildUser();
    when(userRepository.findByEmail("u@test.com")).thenReturn(Optional.of(user));
    when(jwtTokenProvider.generateAccessToken(eq(1L), eq("u@test.com"), eq("USER")))
        .thenReturn("access");
    when(jwtTokenProvider.generateRefreshToken(1L)).thenReturn("refresh");
    when(jwtTokenProvider.getAccessTokenExpiration()).thenReturn(1800000L);

    TokenDTO token = authApplicationService.login(new LoginCommand("u@test.com", "Strong123"));

    assertEquals("access", token.accessToken());
    verify(userRepository).updateFailedAttempts(1L, 0, null);
  }

  @Test
  void should_skip_failed_attempt_reset_for_clean_user_on_login_success() {
    User user = buildUser();
    user.setFailedAttempts(0);
    user.setLockedUntil(null);
    user.setStatus("ACTIVE");
    when(userRepository.findByEmail("u@test.com")).thenReturn(Optional.of(user));
    when(jwtTokenProvider.generateAccessToken(eq(1L), eq("u@test.com"), eq("USER")))
        .thenReturn("access");
    when(jwtTokenProvider.generateRefreshToken(1L)).thenReturn("refresh");
    when(jwtTokenProvider.getAccessTokenExpiration()).thenReturn(1800000L);

    TokenDTO token = authApplicationService.login(new LoginCommand("u@test.com", "Strong123"));

    assertEquals("access", token.accessToken());
    verify(userRepository, never()).updateFailedAttempts(1L, 0, null);
  }

  @Test
  void should_lock_account_after_5_failed_attempts() {
    User user = buildUser();
    user.setFailedAttempts(4);
    when(userRepository.findByEmail("u@test.com")).thenReturn(Optional.of(user));

    assertThrows(
        BizException.class,
        () -> authApplicationService.login(new LoginCommand("u@test.com", "bad")));
    verify(userRepository).updateFailedAttempts(eq(1L), eq(5), any());
  }

  @Test
  void should_refresh_token_and_rotate() {
    when(jwtTokenProvider.getUserIdFromToken("refresh-old")).thenReturn(1L);
    when(jwtTokenProvider.validateRefreshToken("refresh-old", 1L)).thenReturn(true);
    when(userRepository.findById(1L)).thenReturn(Optional.of(buildUser()));
    when(jwtTokenProvider.generateAccessToken(eq(1L), eq("u@test.com"), eq("USER")))
        .thenReturn("access-new");
    when(jwtTokenProvider.generateRefreshToken(1L)).thenReturn("refresh-new");
    when(jwtTokenProvider.getAccessTokenExpiration()).thenReturn(1800000L);

    TokenDTO token = authApplicationService.refreshToken(new RefreshTokenCommand("refresh-old"));

    assertEquals("refresh-new", token.refreshToken());
    verify(jwtTokenProvider).revokeRefreshToken(1L);
  }

  @Test
  void should_blacklist_access_token_on_logout() {
    when(jwtTokenProvider.validateToken("access-token")).thenReturn(true);
    authApplicationService.logout("Bearer access-token");
    verify(jwtTokenProvider).addToBlacklist("access-token");
  }

  @Test
  void should_blacklist_access_token_on_logout_with_raw_token() {
    when(jwtTokenProvider.validateToken("access-token")).thenReturn(true);
    authApplicationService.logout("access-token");
    verify(jwtTokenProvider).addToBlacklist("access-token");
  }

  @Test
  void should_ignore_logout_when_token_invalid() {
    when(jwtTokenProvider.validateToken("bad-token")).thenReturn(false);
    authApplicationService.logout("Bearer bad-token");
    verify(jwtTokenProvider, never()).addToBlacklist(any());
  }

  @Test
  void should_ignore_logout_when_header_missing() {
    authApplicationService.logout(null);
    verify(jwtTokenProvider, never()).addToBlacklist(any());
  }

  @Test
  void should_reset_password_with_valid_otp() {
    when(otpRedisGateway.getStoredOtpHash("u@test.com"))
        .thenReturn(passwordEncoder.encode("123456"));
    when(userRepository.findByEmail("u@test.com")).thenReturn(Optional.of(buildUser()));

    authApplicationService.resetPassword(
        new ResetPasswordCommand("u@test.com", "123456", "NewStrong1"));

    verify(userRepository).updatePassword(eq(1L), any());
    verify(otpRedisGateway).deleteOtp("u@test.com");
  }

  @Test
  void should_reject_weak_password_on_reset() {
    assertThrows(
        BizException.class,
        () ->
            authApplicationService.resetPassword(
                new ResetPasswordCommand("u@test.com", "123456", "weak")));
    verify(otpRedisGateway, never()).getStoredOtpHash(any());
    verify(otpRedisGateway, never()).deleteOtp(any());
  }

  private User buildUser() {
    User user = new User();
    user.setId(1L);
    user.setEmail("u@test.com");
    user.setNickname("tester");
    user.setRole("USER");
    user.setStatus("ACTIVE");
    user.setFailedAttempts(0);
    user.setLockedUntil(Instant.now().minusSeconds(10));
    user.setPasswordHash(passwordEncoder.encode("Strong123"));
    return user;
  }
}
