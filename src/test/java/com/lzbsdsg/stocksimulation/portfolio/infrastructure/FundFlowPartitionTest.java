package com.lzbsdsg.stocksimulation.portfolio.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lzbsdsg.stocksimulation.portfolio.domain.entity.FundFlow;
import com.lzbsdsg.stocksimulation.portfolio.infrastructure.persistence.FundFlowDO;
import com.lzbsdsg.stocksimulation.portfolio.infrastructure.persistence.FundFlowMapper;
import com.lzbsdsg.stocksimulation.portfolio.infrastructure.persistence.FundFlowRepositoryImpl;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** 资金流水分区查询测试（跨月查询 + 分页路由）。 */
class FundFlowPartitionTest {

  private FundFlowMapper fundFlowMapper;
  private FundFlowRepositoryImpl fundFlowRepository;

  @BeforeEach
  void setUp() {
    fundFlowMapper = Mockito.mock(FundFlowMapper.class);
    fundFlowRepository = new FundFlowRepositoryImpl(fundFlowMapper);
  }

  @Test
  void should_query_cross_month_flows() {
    FundFlowDO jan = new FundFlowDO();
    jan.setId(1L);
    jan.setUserId(1001L);
    jan.setFlowType("TRADE_BUY");
    jan.setAmount(new BigDecimal("-1000.00"));
    jan.setBalanceAfter(new BigDecimal("99000.00"));
    jan.setCreatedAt(OffsetDateTime.of(2026, 1, 31, 23, 59, 59, 0, ZoneOffset.ofHours(8)));

    FundFlowDO feb = new FundFlowDO();
    feb.setId(2L);
    feb.setUserId(1001L);
    feb.setFlowType("TRADE_SELL");
    feb.setAmount(new BigDecimal("1100.00"));
    feb.setBalanceAfter(new BigDecimal("100100.00"));
    feb.setCreatedAt(OffsetDateTime.of(2026, 2, 1, 10, 0, 0, 0, ZoneOffset.ofHours(8)));

    when(fundFlowMapper.selectList(any())).thenReturn(List.of(jan, feb));

    List<FundFlow> records =
        fundFlowRepository.findByUserIdAndCreatedAtBetween(
            1001L,
            LocalDateTime.of(2026, 1, 31, 0, 0),
            LocalDateTime.of(2026, 2, 1, 23, 59, 59));

    assertEquals(2, records.size());
    assertEquals(FundFlow.FundFlowType.TRADE_BUY, records.get(0).getFlowType());
    assertEquals(FundFlow.FundFlowType.TRADE_SELL, records.get(1).getFlowType());
  }

  @Test
  void should_route_paginated_query_with_partition_table_transparently() {
    FundFlowDO latest = new FundFlowDO();
    latest.setId(3L);
    latest.setUserId(1001L);
    latest.setFlowType("FREEZE");
    latest.setAmount(new BigDecimal("-500.00"));
    latest.setBalanceAfter(new BigDecimal("99500.00"));
    latest.setCreatedAt(OffsetDateTime.of(2026, 3, 25, 10, 0, 0, 0, ZoneOffset.ofHours(8)));

    Page<FundFlowDO> page = new Page<>(1, 20);
    page.setRecords(List.of(latest));
    when(fundFlowMapper.selectPage(any(Page.class), any())).thenReturn(page);

    List<FundFlow> records = fundFlowRepository.findByUserId(1001L, 1, 20);

    assertEquals(1, records.size());
    assertEquals(FundFlow.FundFlowType.FREEZE, records.get(0).getFlowType());
    assertTrue(records.get(0).getAmount().compareTo(BigDecimal.ZERO) < 0);
  }
}

