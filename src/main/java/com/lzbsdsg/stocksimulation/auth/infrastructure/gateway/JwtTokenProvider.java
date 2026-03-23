package com.lzbsdsg.stocksimulation.auth.infrastructure.gateway;

import com.lzbsdsg.stocksimulation.common.exception.BizException;
import com.lzbsdsg.stocksimulation.common.result.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * JWT Token 提供者
 *
 * <p>负责 JWT 签发、解析、黑名单管理。
 */
@Component
public class JwtTokenProvider {

  private static final String ACCESS_TYPE = "access";
  private static final String REFRESH_TYPE = "refresh";

  private final String secret;
  private final long accessTokenExpiration;
  private final long refreshTokenExpiration;
  private final StringRedisTemplate stringRedisTemplate;

  public JwtTokenProvider(
      @Value("${jwt.secret}") String secret,
      @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
      @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration,
      StringRedisTemplate stringRedisTemplate) {
    this.secret = secret;
    this.accessTokenExpiration = accessTokenExpiration;
    this.refreshTokenExpiration = refreshTokenExpiration;
    this.stringRedisTemplate = stringRedisTemplate;
  }

  /** 签发 Access Token（30min） */
  public String generateAccessToken(Long userId, String email, String role) {
    long exp = Instant.now().toEpochMilli() + accessTokenExpiration;
    String payload =
        String.join(
            "|",
            String.valueOf(userId),
            safe(email),
            safe(role),
            String.valueOf(exp),
            ACCESS_TYPE,
            UUID.randomUUID().toString());
    return sign(payload);
  }

  /** 签发 Refresh Token（7d） */
  public String generateRefreshToken(Long userId) {
    long exp = Instant.now().toEpochMilli() + refreshTokenExpiration;
    String payload =
        String.join(
            "|",
            String.valueOf(userId),
            "-",
            "-",
            String.valueOf(exp),
            REFRESH_TYPE,
            UUID.randomUUID().toString());
    String token = sign(payload);
    stringRedisTemplate
        .opsForValue()
        .set(refreshKey(userId), token, refreshTokenExpiration, TimeUnit.MILLISECONDS);
    return token;
  }

  /** 解析 Token，获取用户ID */
  public Long getUserIdFromToken(String token) {
    TokenParts parts = decodeAndVerify(token);
    return parseUserId(parts);
  }

  /** 验证 Token 有效性（签名 + 过期 + 黑名单） */
  public boolean validateToken(String token) {
    try {
      TokenParts parts = decodeAndVerify(token);
      if (parts.expireAtMillis < Instant.now().toEpochMilli()) {
        return false;
      }
      return !Boolean.TRUE.equals(stringRedisTemplate.hasKey(blacklistKey(token)));
    } catch (Exception ex) {
      return false;
    }
  }

  /** 将 Token 加入黑名单（Redis，TTL=剩余过期时间） */
  public void addToBlacklist(String token) {
    TokenParts parts = decodeAndVerify(token);
    long ttl = Math.max(parts.expireAtMillis - Instant.now().toEpochMilli(), 1L);
    stringRedisTemplate.opsForValue().set(blacklistKey(token), "1", ttl, TimeUnit.MILLISECONDS);
  }

  /** 验证 Refresh Token 并执行 Rotation */
  public boolean validateRefreshToken(String refreshToken, Long userId) {
    try {
      TokenParts parts = decodeAndVerify(refreshToken);
      if (!REFRESH_TYPE.equals(parts.type)) {
        return false;
      }
      if (parts.expireAtMillis < Instant.now().toEpochMilli()) {
        return false;
      }
      if (!userId.equals(parseUserId(parts))) {
        return false;
      }
      String stored = stringRedisTemplate.opsForValue().get(refreshKey(userId));
      return refreshToken.equals(stored);
    } catch (Exception ex) {
      return false;
    }
  }

  /** 使旧 Refresh Token 失效 */
  public void revokeRefreshToken(Long userId) {
    stringRedisTemplate.delete(refreshKey(userId));
  }

  public long getAccessTokenExpiration() {
    return accessTokenExpiration;
  }

  private String refreshKey(Long userId) {
    return "jwt:refresh:" + userId;
  }

  private String blacklistKey(String token) {
    return "jwt:blacklist:" + sha256(token);
  }

  private String safe(String value) {
    return value == null ? "-" : value.replace("|", "");
  }

  private String sign(String payload) {
    String encodedPayload =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    String signature = hmacSha256(encodedPayload, secret);
    return encodedPayload + "." + signature;
  }

  private TokenParts decodeAndVerify(String token) {
    if (token == null || token.isBlank()) {
      throw new BizException(ErrorCode.AUTH_TOKEN_INVALID);
    }
    String[] parts = token.split("\\.");
    if (parts.length != 2) {
      throw new BizException(ErrorCode.AUTH_TOKEN_INVALID);
    }
    String expected = hmacSha256(parts[0], secret);
    if (!MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8), parts[1].getBytes(StandardCharsets.UTF_8))) {
      throw new BizException(ErrorCode.AUTH_TOKEN_INVALID);
    }
    String decoded = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
    String[] values = decoded.split("\\|");
    if (values.length < 6) {
      throw new BizException(ErrorCode.AUTH_TOKEN_INVALID);
    }
    long expireAt;
    try {
      expireAt = Long.parseLong(values[3]);
    } catch (NumberFormatException ex) {
      throw new BizException(ErrorCode.AUTH_TOKEN_INVALID);
    }
    return new TokenParts(values[0], values[1], values[2], expireAt, values[4]);
  }

  private Long parseUserId(TokenParts parts) {
    try {
      return Long.parseLong(parts.userId);
    } catch (NumberFormatException ex) {
      throw new BizException(ErrorCode.AUTH_TOKEN_INVALID);
    }
  }

  private String hmacSha256(String content, String key) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new BizException(ErrorCode.AUTH_TOKEN_INVALID);
    }
  }

  private String sha256(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte b : bytes) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (Exception ex) {
      return String.valueOf(token.hashCode());
    }
  }

  private record TokenParts(
      String userId, String email, String role, long expireAtMillis, String type) {}
}
