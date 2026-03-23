package com.lzbsdsg.stocksimulation.auth.infrastructure.gateway;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * OTP Redis 网关
 *
 * <p>负责验证码的 Redis 存储（BCrypt hash）、TTL 设置、频率限制检查。
 */
@Component
public class OtpRedisGateway {

  private static final Duration OTP_TTL = Duration.ofMinutes(5);
  private static final Duration RATE_LIMIT_TTL = Duration.ofSeconds(60);
  private static final Duration IP_LIMIT_TTL = Duration.ofHours(1);
  private static final long IP_MAX_REQUESTS = 20;

  private final StringRedisTemplate stringRedisTemplate;

  public OtpRedisGateway(StringRedisTemplate stringRedisTemplate) {
    this.stringRedisTemplate = stringRedisTemplate;
  }

  /** 存储 OTP 到 Redis（BCrypt hash，TTL=5min） */
  public void storeOtp(String email, String otpHash) {
    stringRedisTemplate.opsForValue().set(otpKey(email), otpHash, OTP_TTL);
  }

  /** 获取已存储的 OTP hash */
  public String getStoredOtpHash(String email) {
    return stringRedisTemplate.opsForValue().get(otpKey(email));
  }

  /** 验证成功后删除 OTP（一次性消费） */
  public void deleteOtp(String email) {
    stringRedisTemplate.delete(otpKey(email));
  }

  /** 检查发送频率（同一邮箱60s内） */
  public boolean isRateLimited(String email) {
    return Boolean.TRUE.equals(stringRedisTemplate.hasKey(rateKey(email)));
  }

  /** 设置发送频率标记 */
  public void markSent(String email) {
    stringRedisTemplate.opsForValue().set(rateKey(email), "1", RATE_LIMIT_TTL);
  }

  /** 检查 IP 发送限制（每小时20次） */
  public boolean isIpLimited(String ip) {
    String key = ipKey(ip);
    Long count = stringRedisTemplate.opsForValue().increment(key);
    if (count != null && count == 1L) {
      stringRedisTemplate.expire(key, IP_LIMIT_TTL.getSeconds(), TimeUnit.SECONDS);
    }
    return count != null && count > IP_MAX_REQUESTS;
  }

  private String otpKey(String email) {
    return "otp:" + email;
  }

  private String rateKey(String email) {
    return "otp:rate:" + email;
  }

  private String ipKey(String ip) {
    return "otp:ip:" + ip;
  }
}
