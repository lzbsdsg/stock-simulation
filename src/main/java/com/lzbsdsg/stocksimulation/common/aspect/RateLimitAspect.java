package com.lzbsdsg.stocksimulation.common.aspect;

import com.lzbsdsg.stocksimulation.common.annotation.RateLimit;
import com.lzbsdsg.stocksimulation.common.exception.BizException;
import com.lzbsdsg.stocksimulation.common.result.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 限流 AOP 切面
 *
 * <p>基于 Redis + Lua 令牌桶算法实现。
 */
@Aspect
@Component
public class RateLimitAspect {

  private static final Logger log = LoggerFactory.getLogger(RateLimitAspect.class);
  private static final String HEADER_LIMIT = "X-RateLimit-Limit";
  private static final String HEADER_REMAINING = "X-RateLimit-Remaining";
  private static final String HEADER_RESET = "X-RateLimit-Reset";

  private final StringRedisTemplate stringRedisTemplate;
  private final RedisScript<List> rateLimitScript;

  public RateLimitAspect(StringRedisTemplate stringRedisTemplate) {
    this.stringRedisTemplate = stringRedisTemplate;
    DefaultRedisScript<List> script = new DefaultRedisScript<>();
    script.setLocation(new ClassPathResource("lua/rate_limit.lua"));
    script.setResultType(List.class);
    this.rateLimitScript = script;
  }

  @Around("@annotation(com.lzbsdsg.stocksimulation.common.annotation.RateLimit)")
  public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
    RateLimit rateLimit = resolveRateLimit(joinPoint);
    if (rateLimit == null) {
      return joinPoint.proceed();
    }

    long capacity = rateLimit.limit() > 0 ? rateLimit.limit() : rateLimit.maxRequests();
    long windowSeconds =
        rateLimit.window() > 0
            ? rateLimit.window()
            : Math.max(1, rateLimit.timeUnit().toSeconds(rateLimit.timeWindow()));
    double refillRatePerSecond = (double) capacity / windowSeconds;
    long nowMillis = Instant.now().toEpochMilli();

    String identity = resolveIdentity();
    String configuredKey = rateLimit.key().isBlank() ? rateLimit.keyPrefix() : rateLimit.key();
    String keyPrefix =
        configuredKey.isBlank() ? joinPoint.getSignature().toShortString() : configuredKey;
    String rateLimitKey = "rate_limit:" + keyPrefix + ":" + identity;

    List<?> result =
        stringRedisTemplate.execute(
            rateLimitScript,
            Collections.singletonList(rateLimitKey),
            String.valueOf(capacity),
            String.valueOf(refillRatePerSecond),
            String.valueOf(nowMillis),
            "1");

    long allowed = toLong(result, 0, 1L);
    long remaining = toLong(result, 1, Math.max(capacity - 1, 0));
    long resetInSeconds = toLong(result, 2, windowSeconds);
    setLimitHeaders(capacity, remaining, resetInSeconds);

    if (allowed == 0L) {
      log.debug("Rate limited: key={}, remaining={}", rateLimitKey, remaining);
      throw new BizException(ErrorCode.TOO_MANY_REQUESTS);
    }

    return joinPoint.proceed();
  }

  private String resolveIdentity() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.isAuthenticated()
        && !(authentication instanceof AnonymousAuthenticationToken)) {
      return authentication.getName();
    }
    ServletRequestAttributes attributes =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attributes == null) {
      return "anonymous";
    }
    HttpServletRequest request = attributes.getRequest();
    String ip = request.getHeader("X-Forwarded-For");
    if (ip != null && !ip.isBlank()) {
      return ip.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }

  private void setLimitHeaders(long limit, long remaining, long resetInSeconds) {
    ServletRequestAttributes attributes =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attributes == null) {
      return;
    }
    HttpServletResponse response = attributes.getResponse();
    if (response == null) {
      return;
    }
    long resetEpoch = Instant.now().getEpochSecond() + Math.max(resetInSeconds, 1);
    response.setHeader(HEADER_LIMIT, String.valueOf(limit));
    response.setHeader(HEADER_REMAINING, String.valueOf(Math.max(remaining, 0)));
    response.setHeader(HEADER_RESET, String.valueOf(resetEpoch));
  }

  private long toLong(List<?> result, int index, long fallback) {
    if (result == null || result.size() <= index || result.get(index) == null) {
      return fallback;
    }
    Object value = result.get(index);
    if (value instanceof Number number) {
      return number.longValue();
    }
    try {
      return Long.parseLong(String.valueOf(value));
    } catch (NumberFormatException ex) {
      return fallback;
    }
  }

  private RateLimit resolveRateLimit(ProceedingJoinPoint joinPoint) {
    if (!(joinPoint.getSignature() instanceof MethodSignature methodSignature)) {
      return null;
    }
    Class<?> targetClass = joinPoint.getTarget() != null ? joinPoint.getTarget().getClass() : null;
    if (targetClass == null) {
      return null;
    }
    java.lang.reflect.Method method =
        AopUtils.getMostSpecificMethod(methodSignature.getMethod(), targetClass);
    return AnnotatedElementUtils.findMergedAnnotation(method, RateLimit.class);
  }
}
