package com.lzbsdsg.stocksimulation.common.aspect;

import com.lzbsdsg.stocksimulation.common.annotation.RateLimit;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 限流 AOP 切面
 *
 * <p>基于 Redis + Lua 令牌桶算法实现。
 */
@Aspect
@Component
public class RateLimitAspect {

  private static final Logger log = LoggerFactory.getLogger(RateLimitAspect.class);

  // TODO: 注入 RedisTemplate，实现 Lua 脚本限流

  @Around("@annotation(rateLimit)")
  public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
    // TODO: 实现限流逻辑
    // 1. 获取当前用户ID或IP
    // 2. 构建 Redis key: rate:{keyPrefix}:{userId}
    // 3. 执行 Lua 脚本（令牌桶/滑动窗口）
    // 4. 超限则抛出 BizException(ErrorCode.TOO_MANY_REQUESTS)

    return joinPoint.proceed();
  }
}
