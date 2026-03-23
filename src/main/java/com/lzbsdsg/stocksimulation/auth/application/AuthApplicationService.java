package com.lzbsdsg.stocksimulation.auth.application;

import com.lzbsdsg.stocksimulation.auth.application.command.*;
import com.lzbsdsg.stocksimulation.auth.application.dto.TokenDTO;
import com.lzbsdsg.stocksimulation.auth.domain.entity.User;
import com.lzbsdsg.stocksimulation.auth.domain.repository.UserRepository;
import com.lzbsdsg.stocksimulation.auth.domain.service.OtpDomainService;
import com.lzbsdsg.stocksimulation.auth.domain.service.PasswordDomainService;
import com.lzbsdsg.stocksimulation.auth.infrastructure.gateway.EmailGateway;
import com.lzbsdsg.stocksimulation.auth.infrastructure.gateway.JwtTokenProvider;
import com.lzbsdsg.stocksimulation.auth.infrastructure.gateway.OtpRedisGateway;
import com.lzbsdsg.stocksimulation.common.exception.BizException;
import com.lzbsdsg.stocksimulation.common.result.ErrorCode;
import com.lzbsdsg.stocksimulation.config.CaffeineConfig;
import com.lzbsdsg.stocksimulation.user.application.AccountApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Optional;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 认证应用服务
 *
 * <p>编排 OTP 发送/校验、用户注册、密码登录、Token 签发/刷新/登出。
 */
@Service
public class AuthApplicationService {

  private final OtpDomainService otpDomainService;
  private final PasswordDomainService passwordDomainService;
  private final UserRepository userRepository;
  private final AccountApplicationService accountApplicationService;
  private final OtpRedisGateway otpRedisGateway;
  private final JwtTokenProvider jwtTokenProvider;
  private final EmailGateway emailGateway;
  private final PasswordEncoder passwordEncoder;
  private final Cache loginLockCache;

  public AuthApplicationService(
      UserRepository userRepository,
      AccountApplicationService accountApplicationService,
      OtpRedisGateway otpRedisGateway,
      JwtTokenProvider jwtTokenProvider,
      EmailGateway emailGateway,
      PasswordEncoder passwordEncoder,
      CacheManager cacheManager) {
    this.otpDomainService = new OtpDomainService();
    this.passwordDomainService = new PasswordDomainService();
    this.userRepository = userRepository;
    this.accountApplicationService = accountApplicationService;
    this.otpRedisGateway = otpRedisGateway;
    this.jwtTokenProvider = jwtTokenProvider;
    this.emailGateway = emailGateway;
    this.passwordEncoder = passwordEncoder;
    this.loginLockCache = cacheManager.getCache(CaffeineConfig.CACHE_LOGIN_LOCK);
  }

  @Transactional
  public void sendOtp(SendOtpCommand command) {
    String email = command.email().trim().toLowerCase();
    if (otpRedisGateway.isRateLimited(email)) {
      throw new BizException(ErrorCode.AUTH_OTP_SEND_TOO_FREQUENT);
    }
    String ip = currentRequestIp();
    if (otpRedisGateway.isIpLimited(ip)) {
      throw new BizException(ErrorCode.AUTH_OTP_IP_LIMIT);
    }
    String otp = otpDomainService.generateOtp();
    otpRedisGateway.storeOtp(email, passwordEncoder.encode(otp));
    otpRedisGateway.markSent(email);
    emailGateway.sendOtpEmail(email, otp);
  }

  @Transactional
  public TokenDTO register(RegisterCommand command) {
    String email = command.email().trim().toLowerCase();
    if (userRepository.existsByEmail(email)) {
      throw new BizException(ErrorCode.AUTH_EMAIL_ALREADY_REGISTERED);
    }
    if (!passwordDomainService.isPasswordStrong(command.password())) {
      throw new BizException(ErrorCode.AUTH_PASSWORD_TOO_WEAK);
    }
    verifyOtp(email, command.otp());

    User user = new User();
    user.setEmail(email);
    user.setPasswordHash(passwordEncoder.encode(command.password()));
    user.setNickname(command.nickname());
    user.setStatus("ACTIVE");
    user.setRole("USER");
    user.setFailedAttempts(0);

    User saved = userRepository.save(user);
    accountApplicationService.createAccount(saved.getId(), command.initialBalance());
    return issueToken(saved);
  }

  @Transactional
  public TokenDTO login(LoginCommand command) {
    String email = command.email().trim().toLowerCase();
    if (isLockedByCache(email)) {
      throw new BizException(ErrorCode.AUTH_ACCOUNT_LOCKED);
    }

    User user =
        userRepository
            .findByEmail(email)
            .orElseThrow(() -> new BizException(ErrorCode.AUTH_LOGIN_FAILED));

    if (passwordDomainService.isAccountLocked(user)) {
      cacheLock(user);
      throw new BizException(ErrorCode.AUTH_ACCOUNT_LOCKED);
    }

    if (!passwordEncoder.matches(command.password(), user.getPasswordHash())) {
      int failedAttempts = user.getFailedAttempts() + 1;
      Instant lockedUntil = null;
      if (passwordDomainService.shouldLockAccount(failedAttempts)) {
        lockedUntil = passwordDomainService.calculateLockUntil();
      }
      userRepository.updateFailedAttempts(user.getId(), failedAttempts, lockedUntil);
      if (lockedUntil != null) {
        cacheLock(email, lockedUntil);
        throw new BizException(ErrorCode.AUTH_ACCOUNT_LOCKED);
      }
      throw new BizException(ErrorCode.AUTH_LOGIN_FAILED);
    }

    userRepository.updateFailedAttempts(user.getId(), 0, null);
    clearLock(email);
    return issueToken(user);
  }

  @Transactional(readOnly = true)
  public TokenDTO loginByOtp(LoginByOtpCommand command) {
    String email = command.email().trim().toLowerCase();
    verifyOtp(email, command.otp());
    User user =
        userRepository
            .findByEmail(email)
            .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));
    return issueToken(user);
  }

  @Transactional
  public TokenDTO refreshToken(RefreshTokenCommand command) {
    String refreshToken = command.refreshToken();
    Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
    if (!jwtTokenProvider.validateRefreshToken(refreshToken, userId)) {
      throw new BizException(ErrorCode.AUTH_REFRESH_TOKEN_INVALID);
    }
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));
    jwtTokenProvider.revokeRefreshToken(userId);
    return issueToken(user);
  }

  @Transactional
  public void logout(String authorization) {
    String token = extractBearerTokenOrNull(authorization);
    if (token == null) {
      return;
    }
    if (!jwtTokenProvider.validateToken(token)) {
      return;
    }
    jwtTokenProvider.addToBlacklist(token);
  }

  @Transactional
  public void resetPassword(ResetPasswordCommand command) {
    String email = command.email().trim().toLowerCase();
    if (!passwordDomainService.isPasswordStrong(command.newPassword())) {
      throw new BizException(ErrorCode.AUTH_PASSWORD_TOO_WEAK);
    }
    verifyOtp(email, command.otp());
    User user =
        userRepository
            .findByEmail(email)
            .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));
    userRepository.updatePassword(user.getId(), passwordEncoder.encode(command.newPassword()));
    userRepository.updateFailedAttempts(user.getId(), 0, null);
    clearLock(email);
  }

  private void verifyOtp(String email, String otp) {
    String stored = otpRedisGateway.getStoredOtpHash(email);
    if (!otpDomainService.verifyOtp(otp, stored, passwordEncoder::matches)) {
      throw new BizException(ErrorCode.AUTH_OTP_INVALID);
    }
    otpRedisGateway.deleteOtp(email);
  }

  private TokenDTO issueToken(User user) {
    String accessToken =
        jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
    String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());
    return new TokenDTO(
        accessToken,
        refreshToken,
        jwtTokenProvider.getAccessTokenExpiration() / 1000,
        user.getId(),
        user.getNickname());
  }

  private String extractBearerTokenOrNull(String authorization) {
    if (authorization == null) {
      return null;
    }
    String value = authorization.trim();
    if (value.isEmpty()) {
      return null;
    }
    if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
      String token = value.substring(7).trim();
      return token.isEmpty() ? null : token;
    }
    // 兼容直接传 accessToken 的场景（如 Swagger 手工填 header）
    return value;
  }

  private String currentRequestIp() {
    ServletRequestAttributes attributes =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attributes == null) {
      return "unknown";
    }
    HttpServletRequest request = attributes.getRequest();
    String xff = request.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) {
      return xff.split(",")[0].trim();
    }
    return Optional.ofNullable(request.getRemoteAddr()).orElse("unknown");
  }

  private boolean isLockedByCache(String email) {
    if (loginLockCache == null) {
      return false;
    }
    Long lockedUntil = loginLockCache.get(email, Long.class);
    return lockedUntil != null && lockedUntil > Instant.now().toEpochMilli();
  }

  private void cacheLock(User user) {
    cacheLock(user.getEmail(), user.getLockedUntil());
  }

  private void cacheLock(String email, Instant lockedUntil) {
    if (loginLockCache == null || lockedUntil == null) {
      return;
    }
    loginLockCache.put(email, lockedUntil.toEpochMilli());
  }

  private void clearLock(String email) {
    if (loginLockCache != null) {
      loginLockCache.evict(email);
    }
  }
}
