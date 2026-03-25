package com.lzbsdsg.stocksimulation.common.aspect;

import com.lzbsdsg.stocksimulation.common.util.DataSourceContextHolder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 读写分离 AOP 切面。 拦截 @ReadOnly 注解的方法 → 设置 DataSourceContextHolder 为从库。 拦截 @Transactional 注解的方法 → 强制主库。
 *
 * <p>执行顺序：方法执行前设置数据源标记 → 方法执行 → finally 清除 ThreadLocal。 AbstractRoutingDataSource 根据
 * DataSourceContextHolder 决定路由到主库或从库。
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DataSourceAspect {

  @Around("@annotation(com.lzbsdsg.stocksimulation.common.annotation.ReadOnly)")
  public Object aroundReadOnly(ProceedingJoinPoint joinPoint) throws Throwable {
    DataSourceContextHolder.setReadOnly();
    try {
      return joinPoint.proceed();
    } finally {
      DataSourceContextHolder.clear();
    }
  }

  @Around("@annotation(org.springframework.transaction.annotation.Transactional)")
  public Object aroundTransactional(ProceedingJoinPoint joinPoint) throws Throwable {
    DataSourceContextHolder.forceMaster();
    try {
      return joinPoint.proceed();
    } finally {
      DataSourceContextHolder.clear();
    }
  }
}
