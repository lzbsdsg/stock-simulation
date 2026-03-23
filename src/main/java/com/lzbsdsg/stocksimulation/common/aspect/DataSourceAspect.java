package com.lzbsdsg.stocksimulation.common.aspect;

/**
 * 读写分离 AOP 切面。 拦截 @ReadOnly 注解的方法 → 设置 DataSourceContextHolder 为从库。 拦截 @Transactional 注解的方法 → 强制主库。
 *
 * <p>执行顺序：方法执行前设置数据源标记 → 方法执行 → finally 清除 ThreadLocal。 AbstractRoutingDataSource 根据
 * DataSourceContextHolder 决定路由到主库或从库。
 */
public class DataSourceAspect {
  // TODO: @Aspect @Component
  // TODO: @Around("@annotation(ReadOnly)") → DataSourceContextHolder.setReadOnly()
  // TODO: finally → DataSourceContextHolder.clear()
}
