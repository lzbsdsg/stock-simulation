package com.lzbsdsg.stocksimulation.common.aspect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lzbsdsg.stocksimulation.common.annotation.RateLimit;
import com.lzbsdsg.stocksimulation.common.exception.BizException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class RateLimitAspectTest {

  private StringRedisTemplate stringRedisTemplate;
  private RateLimitAspect rateLimitAspect;
  private ProceedingJoinPoint joinPoint;
  private MockHttpServletResponse response;

  @BeforeEach
  void setUp() throws NoSuchMethodException {
    stringRedisTemplate = mock(StringRedisTemplate.class);
    rateLimitAspect = new RateLimitAspect(stringRedisTemplate);
    joinPoint = mock(ProceedingJoinPoint.class);
    MethodSignature signature = mock(MethodSignature.class);
    when(joinPoint.getSignature()).thenReturn(signature);
    when(signature.toShortString()).thenReturn("Dummy.limitMethod()");
    when(signature.getMethod()).thenReturn(Dummy.class.getDeclaredMethod("limitMethod"));
    when(joinPoint.getTarget()).thenReturn(new Dummy());

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("127.0.0.1");
    response = new MockHttpServletResponse();
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
  }

  @AfterEach
  void tearDown() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void should_pass_when_not_exceed_limit() throws Throwable {
    when(stringRedisTemplate.execute(
            any(), anyList(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(List.of(1L, 9L, 60L));
    when(joinPoint.proceed()).thenReturn("ok");

    Object result = rateLimitAspect.around(joinPoint);

    assertEquals("ok", result);
    assertEquals("10", response.getHeader("X-RateLimit-Limit"));
    assertEquals("9", response.getHeader("X-RateLimit-Remaining"));
  }

  @Test
  void should_throw_when_exceed_limit() {
    when(stringRedisTemplate.execute(
            any(), anyList(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(List.of(0L, 0L, 60L));

    assertThrows(BizException.class, () -> rateLimitAspect.around(joinPoint));
    assertEquals("0", response.getHeader("X-RateLimit-Remaining"));
  }

  @Test
  void should_allow_again_when_window_resets() throws Throwable {
    when(stringRedisTemplate.execute(
            any(), anyList(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(List.of(0L, 0L, 1L))
        .thenReturn(List.of(1L, 9L, 60L));
    when(joinPoint.proceed()).thenReturn("ok-after-reset");

    assertThrows(BizException.class, () -> rateLimitAspect.around(joinPoint));

    Object second = rateLimitAspect.around(joinPoint);
    assertEquals("ok-after-reset", second);
  }

  static class Dummy {
    @RateLimit(maxRequests = 10, timeWindow = 60, timeUnit = TimeUnit.SECONDS, keyPrefix = "trade")
    void limitMethod() {}
  }
}
