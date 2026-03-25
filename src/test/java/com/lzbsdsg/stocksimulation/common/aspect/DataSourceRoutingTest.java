package com.lzbsdsg.stocksimulation.common.aspect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lzbsdsg.stocksimulation.common.annotation.ReadOnly;
import com.lzbsdsg.stocksimulation.common.util.DataSourceContextHolder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DataSourceRoutingTest {

  private final DataSourceAspect dataSourceAspect = new DataSourceAspect();

  @AfterEach
  void cleanUp() {
    DataSourceContextHolder.clear();
  }

  @Test
  void should_use_master_by_default() {
    assertEquals(DataSourceContextHolder.DataSourceType.MASTER, DataSourceContextHolder.get());
  }

  @Test
  void should_route_to_slave_for_read_only() throws Throwable {
    ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
    when(joinPoint.proceed()).thenAnswer(invocation -> DataSourceContextHolder.get());

    Object route = dataSourceAspect.aroundReadOnly(joinPoint);

    assertEquals(DataSourceContextHolder.DataSourceType.SLAVE, route);
    assertEquals(DataSourceContextHolder.DataSourceType.MASTER, DataSourceContextHolder.get());
  }

  @Test
  void should_force_master_for_transactional() throws Throwable {
    ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
    when(joinPoint.proceed()).thenAnswer(invocation -> DataSourceContextHolder.get());

    Object route = dataSourceAspect.aroundTransactional(joinPoint);

    assertEquals(DataSourceContextHolder.DataSourceType.MASTER, route);
    assertEquals(DataSourceContextHolder.DataSourceType.MASTER, DataSourceContextHolder.get());
  }

  static class Dummy {
    @ReadOnly
    void readOnlyMethod() {}
  }
}
