package com.lzbsdsg.stocksimulation.auth.infrastructure.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class OtpRedisGatewayIntegrationTest {

  private StringRedisTemplate stringRedisTemplate;
  private ValueOperations<String, String> valueOperations;
  private OtpRedisGateway otpRedisGateway;

  @BeforeEach
  void setUp() {
    stringRedisTemplate = mock(StringRedisTemplate.class);
    valueOperations = mock(ValueOperations.class);
    when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    otpRedisGateway = new OtpRedisGateway(stringRedisTemplate);
  }

  @Test
  void should_store_and_get_otp_hash() {
    otpRedisGateway.storeOtp("u@test.com", "hashed");
    when(valueOperations.get("otp:u@test.com")).thenReturn("hashed");

    String actual = otpRedisGateway.getStoredOtpHash("u@test.com");

    assertEquals("hashed", actual);
  }

  @Test
  void should_rate_limit_flag_work() {
    when(stringRedisTemplate.hasKey("otp:rate:u@test.com")).thenReturn(true);
    assertTrue(otpRedisGateway.isRateLimited("u@test.com"));
    otpRedisGateway.markSent("u@test.com");
  }

  @Test
  void should_ip_limit_work_when_count_exceeds_20() {
    when(valueOperations.increment("otp:ip:127.0.0.1")).thenReturn(21L);
    boolean limited = otpRedisGateway.isIpLimited("127.0.0.1");
    assertTrue(limited);

    when(valueOperations.increment("otp:ip:127.0.0.2")).thenReturn(1L);
    when(stringRedisTemplate.expire(
            "otp:ip:127.0.0.2", 3600L, java.util.concurrent.TimeUnit.SECONDS))
        .thenReturn(true);
    boolean limited2 = otpRedisGateway.isIpLimited("127.0.0.2");
    assertFalse(limited2);
  }
}
