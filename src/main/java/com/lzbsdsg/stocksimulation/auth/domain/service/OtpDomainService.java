package com.lzbsdsg.stocksimulation.auth.domain.service;

import java.security.SecureRandom;

/** OTP 领域服务（纯逻辑，不依赖框架） */
public class OtpDomainService {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final int OTP_LENGTH = 6;

  /** 生成6位数字验证码 */
  public String generateOtp() {
    int otp = RANDOM.nextInt(900000) + 100000;
    return String.valueOf(otp);
  }

  /** 校验验证码是否匹配 */
  public boolean verifyOtp(String inputOtp, String storedOtpHash, OtpVerifier verifier) {
    if (inputOtp == null || storedOtpHash == null) {
      return false;
    }
    return verifier.matches(inputOtp, storedOtpHash);
  }

  /** OTP 验证回调（由 infrastructure 层实现，解耦 BCrypt 依赖） */
  @FunctionalInterface
  public interface OtpVerifier {
    boolean matches(String rawOtp, String encodedOtp);
  }
}
