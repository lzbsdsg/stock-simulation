package com.lzbsdsg.stocksimulation.market.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;

import com.lzbsdsg.stocksimulation.market.domain.entity.KLinePeriod;
import com.lzbsdsg.stocksimulation.market.domain.entity.KLinePoint;
import com.lzbsdsg.stocksimulation.market.domain.entity.MarketKLineSyncState;
import com.lzbsdsg.stocksimulation.market.domain.repository.MarketKLineDailyRepository;
import com.lzbsdsg.stocksimulation.market.domain.repository.MarketKLineSyncStateRepository;
import com.lzbsdsg.stocksimulation.market.infrastructure.adapter.SinaMarketDataAdapter;
import com.lzbsdsg.stocksimulation.market.infrastructure.adapter.TencentMarketDataAdapter;
import com.lzbsdsg.stocksimulation.market.infrastructure.gateway.EastMoneyKLineGateway;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HistoricalKLineServiceTest {

  @Mock private MarketKLineDailyRepository marketKLineDailyRepository;
  @Mock private MarketKLineSyncStateRepository marketKLineSyncStateRepository;
  @Mock private EastMoneyKLineGateway eastMoneyKLineGateway;
  @Mock private SinaMarketDataAdapter sinaMarketDataAdapter;
  @Mock private TencentMarketDataAdapter tencentMarketDataAdapter;

  private HistoricalKLineService historicalKLineService;

  @BeforeEach
  void setUp() {
    historicalKLineService =
        new HistoricalKLineService(
        marketKLineDailyRepository,
        marketKLineSyncStateRepository,
        eastMoneyKLineGateway,
        sinaMarketDataAdapter,
        tencentMarketDataAdapter);
  }

  @Test
  void should_fetch_daily_kline_only_once_per_day_for_same_stock() {
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    LocalDate from = today.minusDays(5);
    LocalDate yesterday = today.minusDays(1);

    when(marketKLineDailyRepository.findEarliestTradeDate("sh600519")).thenReturn(Optional.of(from));
    when(marketKLineDailyRepository.findLatestTradeDate("sh600519")).thenReturn(Optional.of(yesterday));
    when(tencentMarketDataAdapter.getKLine("sh600519", KLinePeriod.DAILY, today, today))
      .thenReturn(List.of(point(today, "1690.00", "1698.00", "1702.00", "1685.00", 120000L, "2000000.00")));
    when(marketKLineDailyRepository.findByStockCodeAndDateRange("sh600519", from, today))
        .thenReturn(
            List.of(
                point(yesterday, "1680.00", "1690.00", "1698.00", "1675.00", 123456L, "2100000.00")));
    when(marketKLineSyncStateRepository.findByStockCode("sh600519"))
        .thenReturn(Optional.empty(), Optional.of(syncState("sh600519", today, yesterday)));

    historicalKLineService.getKLine("sh600519", KLinePeriod.DAILY, from, today);
    historicalKLineService.getKLine("sh600519", KLinePeriod.DAILY, from, today);

    verify(tencentMarketDataAdapter, times(1))
      .getKLine("sh600519", KLinePeriod.DAILY, today, today);
    verify(marketKLineSyncStateRepository, times(1))
        .upsert(org.mockito.ArgumentMatchers.any(MarketKLineSyncState.class));
  }

  @Test
  void should_aggregate_weekly_from_daily_points() {
    LocalDate from = LocalDate.parse("2026-03-02");
    LocalDate to = LocalDate.parse("2026-03-13");
    List<KLinePoint> daily =
        List.of(
            point(LocalDate.parse("2026-03-02"), "10.00", "11.00", "11.20", "9.90", 1000L, "10000.00"),
            point(LocalDate.parse("2026-03-03"), "11.00", "12.00", "12.10", "10.80", 1100L, "12000.00"),
            point(LocalDate.parse("2026-03-04"), "12.00", "12.50", "12.80", "11.90", 900L, "11000.00"),
            point(LocalDate.parse("2026-03-10"), "12.50", "12.20", "12.70", "12.00", 1500L, "18000.00"),
            point(LocalDate.parse("2026-03-11"), "12.20", "13.00", "13.20", "12.10", 1600L, "20000.00"));

    when(marketKLineDailyRepository.findEarliestTradeDate("sh600519")).thenReturn(Optional.of(from));
    when(marketKLineDailyRepository.findLatestTradeDate("sh600519")).thenReturn(Optional.of(to));
    when(marketKLineDailyRepository.findByStockCodeAndDateRange("sh600519", from, to)).thenReturn(daily);

    List<KLinePoint> weekly =
        historicalKLineService.getKLine("sh600519", KLinePeriod.WEEKLY, from, to);

    assertEquals(2, weekly.size());
    assertEquals(LocalDate.parse("2026-03-02"), weekly.get(0).getDate());
    assertEquals(new BigDecimal("10.00"), weekly.get(0).getOpen());
    assertEquals(new BigDecimal("12.50"), weekly.get(0).getClose());
    assertEquals(3000L, weekly.get(0).getVolume());
    assertEquals(LocalDate.parse("2026-03-10"), weekly.get(1).getDate());
    assertEquals(new BigDecimal("13.00"), weekly.get(1).getClose());
  }

  @Test
  void should_clamp_query_to_recent_three_years_and_cleanup_old_rows() {
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    LocalDate lowerBound = today.minusYears(3);
    LocalDate from = today.minusYears(6);
    LocalDate to = today.minusDays(1);

    when(marketKLineDailyRepository.findEarliestTradeDate("sh600519"))
        .thenReturn(Optional.of(lowerBound));
    when(marketKLineDailyRepository.findLatestTradeDate("sh600519")).thenReturn(Optional.empty());
    when(tencentMarketDataAdapter.getKLine("sh600519", KLinePeriod.DAILY, lowerBound, to))
      .thenReturn(List.of(point(lowerBound, "120.00", "121.00", "122.00", "119.50", 10000L, "1200000.00")));
    when(marketKLineDailyRepository.findByStockCodeAndDateRange("sh600519", lowerBound, to))
        .thenReturn(List.of());

    historicalKLineService.getKLine("sh600519", KLinePeriod.DAILY, from, to);

    verify(tencentMarketDataAdapter).getKLine("sh600519", KLinePeriod.DAILY, lowerBound, to);
    verify(marketKLineDailyRepository).deleteOlderThan(eq("sh600519"), eq(lowerBound));
  }

  @Test
    void should_fallback_to_sina_when_tencent_failed() {
    LocalDate from = LocalDate.parse("2026-03-01");
    LocalDate to = LocalDate.parse("2026-03-05");

    when(marketKLineDailyRepository.findEarliestTradeDate("sh600519")).thenReturn(Optional.empty());
    when(marketKLineDailyRepository.findLatestTradeDate("sh600519")).thenReturn(Optional.of(to));
    when(tencentMarketDataAdapter.getKLine("sh600519", KLinePeriod.DAILY, from, to))
        .thenThrow(new IllegalStateException("network error"));
    when(sinaMarketDataAdapter.getKLine("sh600519", KLinePeriod.DAILY, from, to))
        .thenReturn(List.of(point(from, "10.00", "10.20", "10.30", "9.90", 1000L, "10000.00")));
    when(marketKLineDailyRepository.findByStockCodeAndDateRange("sh600519", from, to)).thenReturn(List.of());

    historicalKLineService.getKLine("sh600519", KLinePeriod.DAILY, from, to);

    verify(sinaMarketDataAdapter, org.mockito.Mockito.atLeastOnce())
      .getKLine("sh600519", KLinePeriod.DAILY, from, to);
    verify(marketKLineDailyRepository)
      .upsertBatch(eq("sh600519"), org.mockito.ArgumentMatchers.anyList(), eq("SINA_HISTORY"));
    verify(eastMoneyKLineGateway, never()).fetchDailyKLine("sh600519", from, to);
    }

    @Test
    void should_repair_legacy_synthetic_sources_with_preferred_provider() {
    LocalDate from = LocalDate.parse("2026-01-01");
    LocalDate to = LocalDate.parse("2026-01-31");

    when(marketKLineDailyRepository.findEarliestTradeDate("sh600519")).thenReturn(Optional.of(from));
    when(marketKLineDailyRepository.findLatestTradeDate("sh600519")).thenReturn(Optional.of(to));
    when(marketKLineDailyRepository.findDistinctSourcesInDateRange("sh600519", from, to))
      .thenReturn(List.of("TENCENT"));
    when(tencentMarketDataAdapter.getKLine("sh600519", KLinePeriod.DAILY, from, to))
      .thenReturn(List.of(point(from, "1500.00", "1510.00", "1515.00", "1498.00", 22000L, "33000000.00")));
    when(marketKLineDailyRepository.findByStockCodeAndDateRange("sh600519", from, to))
      .thenReturn(List.of(point(from, "1500.00", "1510.00", "1515.00", "1498.00", 22000L, "33000000.00")));

    historicalKLineService.getKLine("sh600519", KLinePeriod.DAILY, from, to);

    verify(tencentMarketDataAdapter).getKLine("sh600519", KLinePeriod.DAILY, from, to);
    verify(marketKLineDailyRepository)
      .upsertBatch(eq("sh600519"), org.mockito.ArgumentMatchers.anyList(), eq("TENCENT_HISTORY"));
  }

  private KLinePoint point(
      LocalDate date,
      String open,
      String close,
      String high,
      String low,
      long volume,
      String amount) {
    KLinePoint point = new KLinePoint();
    point.setDate(date);
    point.setOpen(new BigDecimal(open));
    point.setClose(new BigDecimal(close));
    point.setHigh(new BigDecimal(high));
    point.setLow(new BigDecimal(low));
    point.setVolume(volume);
    point.setAmount(new BigDecimal(amount));
    return point;
  }

  private MarketKLineSyncState syncState(String code, LocalDate lastSyncDate, LocalDate lastBarDate) {
    MarketKLineSyncState state = new MarketKLineSyncState();
    state.setStockCode(code);
    state.setLastSyncDate(lastSyncDate);
    state.setLastBarDate(lastBarDate);
    return state;
  }
}
