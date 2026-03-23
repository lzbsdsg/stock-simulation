package com.lzbsdsg.stocksimulation.config;

/**
 * 读写分离数据源路由配置。 基于 AbstractRoutingDataSource，根据 DataSourceContextHolder 的 ThreadLocal 标记路由到主库或从库。
 *
 * <p>数据源配置： - master: HikariCP, min=10, max=30 (写操作) - slave: HikariCP, min=20, max=50 (读操作)
 *
 * <p>连接池参数： - connectionTimeout=3000ms - idleTimeout=600000ms - maxLifetime=1800000ms -
 * leakDetectionThreshold=30000ms
 *
 * <p>路由规则： - 默认 → 主库 - @ReadOnly → 从库 - @Transactional → 强制主库 - 复制延迟告警 > 1s
 */
public class DataSourceRoutingConfig {
  // TODO: @Configuration
  // TODO: @Bean DataSource masterDataSource() HikariDataSource
  // TODO: @Bean DataSource slaveDataSource() HikariDataSource
  // TODO: @Bean AbstractRoutingDataSource routingDataSource()
}
