package com.lzbsdsg.stocksimulation.common.util;

/**
 * ThreadLocal 持有当前线程的数据源标记（主库/从库）。 配合 DataSourceRoutingConfig (AbstractRoutingDataSource) 实现读写分离。
 *
 * <p>- 默认: 主库 (MASTER) - @ReadOnly 方法: 从库 (SLAVE) - @Transactional 方法: 强制主库 - 写后立即读: 通过
 * forceMaster() 强制走主库
 *
 * <p>注意: 方法执行完毕后必须 clear()，防止 ThreadLocal 泄漏。
 */
public class DataSourceContextHolder {
  // TODO: private static final ThreadLocal<String> CONTEXT = new ThreadLocal<>();
  // TODO: setReadOnly(), setMaster(), forceMaster(), get(), clear()
}
