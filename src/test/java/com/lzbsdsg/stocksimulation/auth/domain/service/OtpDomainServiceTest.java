package com.lzbsdsg.stocksimulation.auth.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** OTP 领域服务单元测试。 */
public class OtpDomainServiceTest {

  private final OtpDomainService otpDomainService = new OtpDomainService();

  @Test
  void should_generate_6_digit_otp() {
    String otp = otpDomainService.generateOtp();
    assertNotNull(otp);
    assertEquals(6, otp.length());
    assertTrue(otp.matches("\\d{6}"));
  }

  @Test
  void should_verify_valid_otp() {
    assertTrue(
        otpDomainService.verifyOtp("123456", "hashed", (raw, encoded) -> "123456".equals(raw)));
  }

  @Test
  void should_reject_wrong_otp() {
    assertFalse(
        otpDomainService.verifyOtp("654321", "hashed", (raw, encoded) -> "123456".equals(raw)));
  }

  @Test
  void should_reject_null_values() {
    assertFalse(otpDomainService.verifyOtp(null, "hashed", (raw, encoded) -> true));
    assertFalse(otpDomainService.verifyOtp("123456", null, (raw, encoded) -> true));
  }
}
