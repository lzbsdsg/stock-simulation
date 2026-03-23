package com.lzbsdsg.stocksimulation.common.util;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * ThreadLocal 持有当前线程的数据源标记（主库/从库）。 配合 DataSourceRoutingConfig (AbstractRoutingDataSource) 实现读写分离。
 *
 * <p>- 默认: 主库 (MASTER) - @ReadOnly 方法: 从库 (SLAVE) - @Transactional 方法: 强制主库 - 写后立即读: 通过
 * forceMaster() 强制走主库
 *
 * <p>注意: 方法执行完毕后必须 clear()，防止 ThreadLocal 泄漏。
 */
public class DataSourceContextHolder {

  public enum DataSourceType {
    MASTER,
    SLAVE
  }

  private static final ThreadLocal<Deque<DataSourceType>> CONTEXT_STACK =
      ThreadLocal.withInitial(ArrayDeque::new);

  private DataSourceContextHolder() {}

  public static void setReadOnly() {
    CONTEXT_STACK.get().push(DataSourceType.SLAVE);
  }

  public static void setMaster() {
    CONTEXT_STACK.get().push(DataSourceType.MASTER);
  }

  public static void forceMaster() {
    setMaster();
  }

  public static DataSourceType get() {
    Deque<DataSourceType> stack = CONTEXT_STACK.get();
    return stack.isEmpty() ? DataSourceType.MASTER : stack.peek();
  }

  public static void clear() {
    Deque<DataSourceType> stack = CONTEXT_STACK.get();
    if (!stack.isEmpty()) {
      stack.pop();
    }
    if (stack.isEmpty()) {
      CONTEXT_STACK.remove();
    }
  }
}
