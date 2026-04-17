package com.lzbsdsg.stocksimulation.config;

import com.lzbsdsg.stocksimulation.common.util.DataSourceContextHolder;
import com.zaxxer.hikari.HikariDataSource;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

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
@Configuration
public class DataSourceRoutingConfig {

  private static final int MASTER_MIN_IDLE = 10;
  private static final int MASTER_MAX_POOL = 30;
  private static final int SLAVE_MIN_IDLE = 20;
  private static final int SLAVE_MAX_POOL = 50;
  private static final long DEFAULT_CONNECTION_TIMEOUT_MS = 3000L;
  private static final long DEFAULT_IDLE_TIMEOUT_MS = 600000L;
  private static final long DEFAULT_MAX_LIFETIME_MS = 1800000L;
  private static final long DEFAULT_KEEPALIVE_TIME_MS = 120000L;
  private static final long DEFAULT_LEAK_DETECTION_MS = 30000L;

  @Bean(name = "masterDataSource")
  public DataSource masterDataSource(Environment environment) {
    return buildDataSource(
        environment,
        "spring.datasource.master",
        "spring.datasource",
        MASTER_MIN_IDLE,
        MASTER_MAX_POOL,
        "master-pool");
  }

  @Bean(name = "slaveDataSource")
  public DataSource slaveDataSource(Environment environment) {
    return buildDataSource(
        environment,
        "spring.datasource.slave",
        "spring.datasource",
        SLAVE_MIN_IDLE,
        SLAVE_MAX_POOL,
        "slave-pool");
  }

  @Bean(name = "routingDataSource")
  public AbstractRoutingDataSource routingDataSource(
      @Qualifier("masterDataSource") DataSource masterDataSource,
      @Qualifier("slaveDataSource") DataSource slaveDataSource) {
    Map<Object, Object> targetDataSources = new HashMap<>();
    targetDataSources.put(DataSourceContextHolder.DataSourceType.MASTER, masterDataSource);
    targetDataSources.put(DataSourceContextHolder.DataSourceType.SLAVE, slaveDataSource);

    AbstractRoutingDataSource routingDataSource =
        new AbstractRoutingDataSource() {
          @Override
          protected Object determineCurrentLookupKey() {
            return DataSourceContextHolder.get();
          }
        };
    routingDataSource.setDefaultTargetDataSource(masterDataSource);
    routingDataSource.setTargetDataSources(targetDataSources);
    routingDataSource.afterPropertiesSet();
    return routingDataSource;
  }

  @Bean
  @Primary
  public DataSource dataSource(
      @Qualifier("routingDataSource") AbstractRoutingDataSource routingDataSource) {
    return routingDataSource;
  }

  private DataSource buildDataSource(
      Environment environment,
      String preferredPrefix,
      String fallbackPrefix,
      int defaultMinIdle,
      int defaultMaxPoolSize,
      String poolName) {
    HikariDataSource ds = new HikariDataSource();
    ds.setDriverClassName(
        get(
            environment,
            preferredPrefix + ".driver-class-name",
            fallbackPrefix + ".driver-class-name",
            "org.postgresql.Driver"));
    ds.setJdbcUrl(
        get(
            environment,
            preferredPrefix + ".url",
            fallbackPrefix + ".url",
            "jdbc:postgresql://localhost:5432/stock_simulation"));
    ds.setUsername(
        get(environment, preferredPrefix + ".username", fallbackPrefix + ".username", "postgres"));
    ds.setPassword(
        get(environment, preferredPrefix + ".password", fallbackPrefix + ".password", ""));

    String preferredHikariPrefix = preferredPrefix + ".hikari";
    String fallbackHikariPrefix = fallbackPrefix + ".hikari";
    int minIdle =
        getInt(
            environment,
            preferredHikariPrefix + ".minimum-idle",
            fallbackHikariPrefix + ".minimum-idle",
            defaultMinIdle);
    int maxPoolSize =
        getInt(
            environment,
            preferredHikariPrefix + ".maximum-pool-size",
            fallbackHikariPrefix + ".maximum-pool-size",
            defaultMaxPoolSize);
    long connectionTimeout =
        getLong(
            environment,
            preferredHikariPrefix + ".connection-timeout",
            fallbackHikariPrefix + ".connection-timeout",
            DEFAULT_CONNECTION_TIMEOUT_MS);
    long idleTimeout =
        getLong(
            environment,
            preferredHikariPrefix + ".idle-timeout",
            fallbackHikariPrefix + ".idle-timeout",
            DEFAULT_IDLE_TIMEOUT_MS);
    long maxLifetime =
        getLong(
            environment,
            preferredHikariPrefix + ".max-lifetime",
            fallbackHikariPrefix + ".max-lifetime",
            DEFAULT_MAX_LIFETIME_MS);
    long keepaliveTime =
        getLong(
            environment,
            preferredHikariPrefix + ".keepalive-time",
            fallbackHikariPrefix + ".keepalive-time",
            DEFAULT_KEEPALIVE_TIME_MS);
    long leakDetectionThreshold =
        getLong(
            environment,
            preferredHikariPrefix + ".leak-detection-threshold",
            fallbackHikariPrefix + ".leak-detection-threshold",
            DEFAULT_LEAK_DETECTION_MS);

    ds.setMinimumIdle(Math.max(1, minIdle));
    ds.setMaximumPoolSize(Math.max(Math.max(1, minIdle), maxPoolSize));
    ds.setConnectionTimeout(Math.max(1000L, connectionTimeout));
    ds.setIdleTimeout(Math.max(30000L, idleTimeout));
    ds.setMaxLifetime(Math.max(60000L, maxLifetime));
    if (keepaliveTime > 0) {
      ds.setKeepaliveTime(keepaliveTime);
    }
    if (leakDetectionThreshold > 0) {
      ds.setLeakDetectionThreshold(leakDetectionThreshold);
    }
    ds.setPoolName(poolName);
    return ds;
  }

  private String get(Environment environment, String key, String fallbackKey, String defaultValue) {
    String preferred = environment.getProperty(key);
    if (preferred != null && !preferred.isBlank()) {
      return preferred;
    }
    String fallback = environment.getProperty(fallbackKey);
    if (fallback != null && !fallback.isBlank()) {
      return fallback;
    }
    return defaultValue;
  }

  private int getInt(Environment environment, String key, String fallbackKey, int defaultValue) {
    String value = get(environment, key, fallbackKey, String.valueOf(defaultValue));
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException ignored) {
      return defaultValue;
    }
  }

  private long getLong(Environment environment, String key, String fallbackKey, long defaultValue) {
    String value = get(environment, key, fallbackKey, String.valueOf(defaultValue));
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException ignored) {
      return defaultValue;
    }
  }
}
