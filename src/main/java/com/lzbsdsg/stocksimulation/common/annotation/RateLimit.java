package com.lzbsdsg.stocksimulation.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 接口限流注解
 *
 * <p>使用示例: @RateLimit(maxRequests = 10, timeWindow = 1, timeUnit = TimeUnit.MINUTES)
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

  /** 时间窗口内最大请求数 */
  int maxRequests() default 100;

  /** 时间窗口大小 */
  long timeWindow() default 1;

  /** 时间窗口单位 */
  TimeUnit timeUnit() default TimeUnit.MINUTES;

  /** 限流维度 key 前缀（默认按用户ID限流） */
  String keyPrefix() default "";
}
