package com.lzbsdsg.stocksimulation.market.domain.service;

import com.lzbsdsg.stocksimulation.market.domain.entity.KLinePeriod;
import com.lzbsdsg.stocksimulation.market.domain.entity.KLinePoint;
import com.lzbsdsg.stocksimulation.market.domain.entity.MarketKLineSyncState;
import com.lzbsdsg.stocksimulation.market.domain.repository.MarketKLineDailyRepository;
import com.lzbsdsg.stocksimulation.market.domain.repository.MarketKLineSyncStateRepository;
import com.lzbsdsg.stocksimulation.market.infrastructure.adapter.SinaMarketDataAdapter;
import com.lzbsdsg.stocksimulation.market.infrastructure.adapter.TencentMarketDataAdapter;
import com.lzbsdsg.stocksimulation.market.infrastructure.gateway.EastMoneyKLineGateway;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 历史K线服务：真实日K落库，并按日维度做增量同步。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HistoricalKLineService {

  private static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");
  private static final String DATA_SOURCE = "EASTMONEY";
  private static final String DATA_SOURCE_SINA = "SINA";
  private static final String DATA_SOURCE_TENCENT = "TENCENT";
  private static final int RETAIN_YEARS = 3;

  private final MarketKLineDailyRepository marketKLineDailyRepository;
  private final MarketKLineSyncStateRepository marketKLineSyncStateRepository;
  private final EastMoneyKLineGateway eastMoneyKLineGateway;
  private final SinaMarketDataAdapter sinaMarketDataAdapter;
  private final TencentMarketDataAdapter tencentMarketDataAdapter;

  public List<KLinePoint> getKLine(String stockCode, KLinePeriod period, LocalDate from, LocalDate to) {
    if (from == null || to == null || from.isAfter(to)) {
      return List.of();
    }

    String normalizedCode = normalizeStockCode(stockCode);
    LocalDate today = LocalDate.now(ZONE_SHANGHAI);
    LocalDate lowerBound = today.minusYears(RETAIN_YEARS);
    LocalDate boundedFrom = from.isBefore(lowerBound) ? lowerBound : from;
    LocalDate boundedTo = to.isAfter(today) ? today : to;
    if (boundedFrom.isAfter(boundedTo)) {
      return List.of();
    }

    syncDailyKLine(normalizedCode, boundedFrom, boundedTo);
    marketKLineDailyRepository.deleteOlderThan(normalizedCode, lowerBound);
    List<KLinePoint> dailyPoints =
        marketKLineDailyRepository.findByStockCodeAndDateRange(normalizedCode, boundedFrom, boundedTo);
    if (period == KLinePeriod.DAILY) {
      return dailyPoints;
    }
    return aggregate(dailyPoints, period);
  }

  private void syncDailyKLine(String stockCode, LocalDate from, LocalDate to) {
    backfillHeadIfNeeded(stockCode, from, to);
    refreshTailIfNeeded(stockCode, from, to);
  }

  private void backfillHeadIfNeeded(String stockCode, LocalDate from, LocalDate to) {
    LocalDate earliest = marketKLineDailyRepository.findEarliestTradeDate(stockCode).orElse(null);
    if (earliest != null && !earliest.isAfter(from)) {
      return;
    }
    LocalDate backfillTo = earliest == null ? to : earliest.minusDays(1);
    if (from.isAfter(backfillTo)) {
      return;
    }
    fetchAndUpsert(stockCode, from, backfillTo);
  }

  private void refreshTailIfNeeded(String stockCode, LocalDate from, LocalDate to) {
    LocalDate latest = marketKLineDailyRepository.findLatestTradeDate(stockCode).orElse(null);
    LocalDate fetchFrom = latest == null ? from : latest.plusDays(1);
    if (fetchFrom.isAfter(to)) {
      return;
    }

    LocalDate today = LocalDate.now(ZONE_SHANGHAI);
    if (to.isEqual(today)) {
      MarketKLineSyncState state =
          marketKLineSyncStateRepository.findByStockCode(stockCode).orElse(null);
      if (state != null && today.equals(state.getLastSyncDate())) {
        return;
      }
      fetchAndUpsert(stockCode, fetchFrom, to);
      upsertSyncState(stockCode, today);
      return;
    }

    fetchAndUpsert(stockCode, fetchFrom, to);
  }

  private void fetchAndUpsert(String stockCode, LocalDate from, LocalDate to) {
    try {
      List<KLinePoint> fetched = eastMoneyKLineGateway.fetchDailyKLine(stockCode, from, to);
      if (!fetched.isEmpty()) {
        marketKLineDailyRepository.upsertBatch(stockCode, fetched, DATA_SOURCE);
        return;
      }
      fetchAndUpsertFromFallbackProviders(stockCode, from, to);
    } catch (Exception ex) {
      log.warn(
          "Fetch real daily kline failed stockCode={} from={} to={} reason={}",
          stockCode,
          from,
          to,
          ex.getMessage());
      fetchAndUpsertFromFallbackProviders(stockCode, from, to);
    }
  }

  private void fetchAndUpsertFromFallbackProviders(String stockCode, LocalDate from, LocalDate to) {
    List<KLinePoint> sinaPoints = fetchKLineFromSina(stockCode, from, to);
    if (!sinaPoints.isEmpty()) {
      marketKLineDailyRepository.upsertBatch(stockCode, sinaPoints, DATA_SOURCE_SINA);
      return;
    }

    List<KLinePoint> tencentPoints = fetchKLineFromTencent(stockCode, from, to);
    if (!tencentPoints.isEmpty()) {
      marketKLineDailyRepository.upsertBatch(stockCode, tencentPoints, DATA_SOURCE_TENCENT);
    }
  }

  private List<KLinePoint> fetchKLineFromSina(String stockCode, LocalDate from, LocalDate to) {
    try {
      return sinaMarketDataAdapter.getKLine(stockCode, KLinePeriod.DAILY, from, to);
    } catch (Exception ex) {
      log.warn(
          "Sina kline fallback failed stockCode={} from={} to={} reason={}",
          stockCode,
          from,
          to,
          ex.getMessage());
      return List.of();
    }
  }

  private List<KLinePoint> fetchKLineFromTencent(String stockCode, LocalDate from, LocalDate to) {
    try {
      return tencentMarketDataAdapter.getKLine(stockCode, KLinePeriod.DAILY, from, to);
    } catch (Exception ex) {
      log.warn(
          "Tencent kline fallback failed stockCode={} from={} to={} reason={}",
          stockCode,
          from,
          to,
          ex.getMessage());
      return List.of();
    }
  }

  private void upsertSyncState(String stockCode, LocalDate syncDate) {
    MarketKLineSyncState state = new MarketKLineSyncState();
    state.setStockCode(stockCode);
    state.setLastSyncDate(syncDate);
    state.setLastBarDate(marketKLineDailyRepository.findLatestTradeDate(stockCode).orElse(null));
    marketKLineSyncStateRepository.upsert(state);
  }

  private List<KLinePoint> aggregate(List<KLinePoint> dailyPoints, KLinePeriod period) {
    Map<LocalDate, KLineAccumulator> grouped = new LinkedHashMap<>();
    for (KLinePoint point : dailyPoints) {
      if (point == null || point.getDate() == null) {
        continue;
      }
      LocalDate bucket =
          switch (period) {
            case WEEKLY -> point.getDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTHLY -> point.getDate().withDayOfMonth(1);
            default -> point.getDate();
          };
      grouped.computeIfAbsent(bucket, ignored -> new KLineAccumulator()).accept(point);
    }
    return grouped.values().stream().map(KLineAccumulator::toPoint).toList();
  }

  private String normalizeStockCode(String stockCode) {
    if (stockCode == null) {
      return "";
    }
    String code = stockCode.trim().toLowerCase(Locale.ROOT);
    if (code.startsWith("sh") || code.startsWith("sz") || code.startsWith("bj")) {
      return code;
    }
    if (code.matches("^6\\d{5}$")) {
      return "sh" + code;
    }
    if (code.matches("^[03]\\d{5}$")) {
      return "sz" + code;
    }
    if (code.matches("^[48]\\d{5}$")) {
      return "bj" + code;
    }
    return code;
  }

  private static final class KLineAccumulator {

    private LocalDate firstDate;
    private BigDecimal open;
    private BigDecimal close;
    private BigDecimal high;
    private BigDecimal low;
    private long volume;
    private BigDecimal amount = BigDecimal.ZERO;

    void accept(KLinePoint point) {
      if (firstDate == null) {
        firstDate = point.getDate();
        open = point.getOpen();
        high = point.getHigh();
        low = point.getLow();
      }
      close = point.getClose();
      if (point.getHigh() != null && (high == null || point.getHigh().compareTo(high) > 0)) {
        high = point.getHigh();
      }
      if (point.getLow() != null && (low == null || point.getLow().compareTo(low) < 0)) {
        low = point.getLow();
      }
      volume += point.getVolume() == null ? 0L : point.getVolume();
      if (point.getAmount() != null) {
        amount = amount.add(point.getAmount());
      }
    }

    KLinePoint toPoint() {
      KLinePoint point = new KLinePoint();
      point.setDate(firstDate);
      point.setOpen(open);
      point.setClose(close);
      point.setHigh(high);
      point.setLow(low);
      point.setVolume(volume);
      point.setAmount(amount);
      return point;
    }
  }
}
