package com.lzbsdsg.stocksimulation.config;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;

/** 注册读写分离数据源的连接池活跃连接指标。 */
@Configuration
public class DbPoolMetricsConfig {

  public DbPoolMetricsConfig(
      MeterRegistry meterRegistry,
      @Qualifier("masterDataSource") DataSource masterDataSource,
      @Qualifier("slaveDataSource") DataSource slaveDataSource) {
    registerGauge(meterRegistry, masterDataSource, "master");
    registerGauge(meterRegistry, slaveDataSource, "slave");
  }

  private void registerGauge(MeterRegistry meterRegistry, DataSource dataSource, String source) {
    if (!(dataSource instanceof HikariDataSource hikariDataSource)) {
      return;
    }
    Gauge.builder("db_pool_active_connections", hikariDataSource, this::activeConnections)
        .description("Active connections in routed database pools")
        .tag("source", source)
        .register(meterRegistry);
  }

  private double activeConnections(HikariDataSource dataSource) {
    HikariPoolMXBean poolMxBean = dataSource.getHikariPoolMXBean();
    if (poolMxBean == null) {
      return 0d;
    }
    return poolMxBean.getActiveConnections();
  }
}
