package com.lzbsdsg.stocksimulation.auth.infrastructure.gateway;

import org.springframework.stereotype.Component;

/**
 * OTP Redis 网关
 *
 * <p>负责验证码的 Redis 存储（BCrypt hash）、TTL 设置、频率限制检查。
 */
@Component
public class OtpRedisGateway {

  // TODO: 注入 RedisTemplate<String, String>

  /** 存储 OTP 到 Redis（BCrypt hash，TTL=5min） */
  public void storeOtp(String email, String otpHash) {
    // TODO: SET otp:{email} → otpHash, TTL=300s
  }

  /** 获取已存储的 OTP hash */
  public String getStoredOtpHash(String email) {
    // TODO: GET otp:{email}
    return null;
  }

  /** 验证成功后删除 OTP（一次性消费） */
  public void deleteOtp(String email) {
    // TODO: DEL otp:{email}
  }

  /** 检查发送频率（同一邮箱60s内） */
  public boolean isRateLimited(String email) {
    // TODO: EXISTS otp:rate:{email}
    return false;
  }

  /** 设置发送频率标记 */
  public void markSent(String email) {
    // TODO: SETNX otp:rate:{email}, TTL=60s
  }

  /** 检查 IP 发送限制（每小时20次） */
  public boolean isIpLimited(String ip) {
    // TODO: INCR otp:ip:{ip}, TTL=3600s, max=20
    return false;
  }
}
