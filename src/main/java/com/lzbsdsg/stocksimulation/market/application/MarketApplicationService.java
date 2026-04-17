package com.lzbsdsg.stocksimulation.market.application;

import com.lzbsdsg.stocksimulation.common.exception.BizException;
import com.lzbsdsg.stocksimulation.common.result.PageResult;
import com.lzbsdsg.stocksimulation.common.result.ErrorCode;
import com.lzbsdsg.stocksimulation.config.CaffeineConfig;
import com.lzbsdsg.stocksimulation.market.application.vo.KLineVO;
import com.lzbsdsg.stocksimulation.market.application.vo.MarketLatencyMetricVO;
import com.lzbsdsg.stocksimulation.market.application.vo.MarketIndexQuoteVO;
import com.lzbsdsg.stocksimulation.market.application.vo.MarketRealtimeMetricsVO;
import com.lzbsdsg.stocksimulation.market.application.vo.QuoteVO;
import com.lzbsdsg.stocksimulation.market.application.vo.StockListedItemVO;
import com.lzbsdsg.stocksimulation.market.domain.entity.KLinePeriod;
import com.lzbsdsg.stocksimulation.market.domain.entity.KLinePoint;
import com.lzbsdsg.stocksimulation.market.domain.entity.QuoteSnapshot;
import com.lzbsdsg.stocksimulation.market.domain.entity.StockInfo;
import com.lzbsdsg.stocksimulation.market.domain.repository.StockInfoRepository;
import com.lzbsdsg.stocksimulation.market.domain.service.MarketDataFacade;
import com.lzbsdsg.stocksimulation.market.infrastructure.ingest.MarketActiveQuoteRegistry;
import com.lzbsdsg.stocksimulation.market.infrastructure.ingest.MarketIngestService;
import com.lzbsdsg.stocksimulation.market.infrastructure.gateway.EastMoneyOfficialBoardGateway;
import com.lzbsdsg.stocksimulation.market.infrastructure.websocket.MarketWebSocketHandler;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** 行情应用服务 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketApplicationService {

  private static final String STOCK_LIST_CACHE_KEY = "listed-all";
  private static final int SEARCH_LIMIT = 20;
  private static final int DEFAULT_LIST_SIZE = 40;
  private static final int MAX_LIST_SIZE = 200;
  private static final String INGEST_CYCLE_TIMER_METRIC = "market.ingest.cycle.duration";
  private static final String PUBSUB_FANOUT_TIMER_METRIC = "market.pubsub.fanout.delay";
  private static final String WS_QUEUE_DELAY_TIMER_METRIC = "market.ws.queue.delay";
  private static final String WS_PUSH_TIMER_METRIC = "ws_push_duration_seconds";
  private static final List<String> INDEX_CODES = List.of("sh000001", "sz399001", "sh000300");

  private final MarketDataFacade marketDataFacade;
  private final StockInfoRepository stockInfoRepository;
  private final CacheManager cacheManager;
  private final MarketActiveQuoteRegistry marketActiveQuoteRegistry;
  private final MarketIngestService marketIngestService;
  private final EastMoneyOfficialBoardGateway eastMoneyOfficialBoardGateway;
  private final MarketWebSocketHandler marketWebSocketHandler;
  private final MeterRegistry meterRegistry;

  private volatile List<MarketIndexQuoteVO> indexQuoteCache = List.of();

  @Value("${market.ingest.active-window-ms:8000}")
  private long activeWindowMs;

  @PostConstruct
  void warmupOfficialBoardCache() {
    refreshOfficialBoardCache();
  }

  public QuoteVO getQuote(String stockCode) {
    QuoteSnapshot snapshot = marketDataFacade.getQuote(stockCode);
    return toQuoteVO(snapshot);
  }

  public List<QuoteVO> batchGetQuotes(List<String> stockCodes) {
    if (stockCodes == null || stockCodes.isEmpty()) {
      throw new BizException(ErrorCode.BAD_REQUEST, "stockCodes must not be empty");
    }
    List<QuoteSnapshot> snapshots = marketDataFacade.batchGetQuotes(stockCodes);
    return snapshots.stream().map(this::toQuoteVO).collect(Collectors.toList());
  }

  public List<KLineVO> getKLine(String stockCode, String period, LocalDate from, LocalDate to) {
    KLinePeriod kLinePeriod = KLinePeriod.valueOf(period.toUpperCase(Locale.ROOT));
    List<KLinePoint> points = marketDataFacade.getKLine(stockCode, kLinePeriod, from, to);
    return points.stream().map(this::toKLineVO).collect(Collectors.toList());
  }

  public List<QuoteVO> searchStock(String keyword) {
    String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
    if (normalizedKeyword.isEmpty()) {
      throw new BizException(ErrorCode.BAD_REQUEST, "keyword must not be blank");
    }

    List<StockInfo> listedStocks = getOrLoadListedStocks();
    List<StockInfo> matchedStocks = listedStocks.stream()
        .filter(this::isListed)
        .filter(
            stock -> containsIgnoreCase(stock.getStockCode(), normalizedKeyword)
                || containsIgnoreCase(stock.getStockName(), normalizedKeyword))
        .limit(SEARCH_LIMIT)
        .toList();

    List<QuoteVO> results = new ArrayList<>();
    for (StockInfo stockInfo : matchedStocks) {
      try {
        results.add(toQuoteVO(marketDataFacade.getQuote(stockInfo.getStockCode())));
      } catch (Exception ex) {
        results.add(fallbackQuote(stockInfo));
      }
    }
    return results;
  }

  public PageResult<StockListedItemVO> getListedStocks(int page, int size) {
    int safePage = Math.max(page, 1);
    int safeSize = size <= 0 ? DEFAULT_LIST_SIZE : Math.min(size, MAX_LIST_SIZE);
    List<StockInfo> listedStocks =
        getOrLoadListedStocks().stream()
            .filter(this::isListed)
            .sorted(Comparator.comparing(StockInfo::getStockCode, String.CASE_INSENSITIVE_ORDER))
            .toList();

    int total = listedStocks.size();
    int fromIndex = Math.min((safePage - 1) * safeSize, total);
    int toIndex = Math.min(fromIndex + safeSize, total);
    List<StockListedItemVO> records =
        listedStocks.subList(fromIndex, toIndex).stream()
            .map(stock -> new StockListedItemVO(stock.getStockCode(), stock.getStockName()))
            .toList();
    return new PageResult<>(records, total, safePage, safeSize);
  }

  public List<MarketIndexQuoteVO> getOfficialIndexQuotes() {
    if (indexQuoteCache.isEmpty()) {
      refreshOfficialBoardCache();
    }

    if (!indexQuoteCache.isEmpty()) {
      return indexQuoteCache;
    }

    // 指数接口保留多源行情兜底，避免官方短时不可用时前端完全空白。
    List<MarketIndexQuoteVO> fallback = buildFallbackIndexQuotes();
    if (!fallback.isEmpty()) {
      indexQuoteCache = fallback;
    }
    return indexQuoteCache;
  }

  @Scheduled(fixedRateString = "${market.board.refresh-interval-ms:15000}")
  public void refreshOfficialBoardCache() {
    try {
      List<MarketIndexQuoteVO> officialIndexes = eastMoneyOfficialBoardGateway.fetchIndexQuotes();
      if (!officialIndexes.isEmpty()) {
        indexQuoteCache = List.copyOf(officialIndexes);
      }
    } catch (Exception ex) {
      log.warn("Refresh official index cache failed, keep previous cache: {}", ex.getMessage());
    }
  }

  public void reportVisibleCodes(List<String> stockCodes) {
    marketActiveQuoteRegistry.reportVisibleCodes(stockCodes);
  }

  public MarketRealtimeMetricsVO getRealtimeMetrics() {
    long activeCodeCount = marketActiveQuoteRegistry.countActiveCodes(Duration.ofMillis(Math.max(activeWindowMs, 1L)));
    return new MarketRealtimeMetricsVO(
        LocalDateTime.now(),
        activeCodeCount,
        marketIngestService.getLastIngestCodeCount(),
        marketIngestService.getLastPublishedQuoteCount(),
        marketIngestService.getLastIngestDurationMs(),
        marketWebSocketHandler.getActiveConnectionCount(),
        marketWebSocketHandler.getQueuedTaskCount(),
        marketWebSocketHandler.isDegradedMode(),
        marketWebSocketHandler.getDroppedTotal(),
        toLatencyMetric(INGEST_CYCLE_TIMER_METRIC),
        toLatencyMetric(PUBSUB_FANOUT_TIMER_METRIC),
        toLatencyMetric(WS_QUEUE_DELAY_TIMER_METRIC),
        toLatencyMetric(WS_PUSH_TIMER_METRIC));
  }

  // ---- Converter ----

  private QuoteVO toQuoteVO(QuoteSnapshot s) {
    return new QuoteVO(
        s.getStockCode(),
        s.getStockName(),
        s.getCurrentPrice(),
        s.getOpenPrice(),
        s.getClosePrice(),
        s.getHighPrice(),
        s.getLowPrice(),
        s.getVolume(),
        s.getAmount(),
        s.getChangePercent(),
        s.getTimestamp());
  }

  private QuoteVO fallbackQuote(StockInfo stockInfo) {
    return new QuoteVO(
        stockInfo.getStockCode(),
        stockInfo.getStockName(),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        LocalDateTime.now());
  }

  private List<MarketIndexQuoteVO> buildFallbackIndexQuotes() {
    try {
      List<QuoteSnapshot> snapshots = marketDataFacade.batchGetQuotes(INDEX_CODES);
      return snapshots.stream()
          .map(
              snapshot -> {
                BigDecimal changeAmount = null;
                if (snapshot.getCurrentPrice() != null && snapshot.getClosePrice() != null) {
                  changeAmount = snapshot.getCurrentPrice().subtract(snapshot.getClosePrice());
                }
                return new MarketIndexQuoteVO(
                    snapshot.getStockCode(),
                    snapshot.getStockName(),
                    snapshot.getCurrentPrice(),
                    changeAmount,
                    snapshot.getChangePercent(),
                    snapshot.getVolume(),
                    snapshot.getAmount());
              })
          .toList();
    } catch (Exception ex) {
      log.warn("Build fallback index quotes failed: {}", ex.getMessage());
      return List.of();
    }
  }

  private List<StockInfo> getOrLoadListedStocks() {
    Cache stockCache = cacheManager.getCache(CaffeineConfig.CACHE_STOCK);
    if (stockCache == null) {
      return stockInfoRepository.findAllListed();
    }

    Object cached = stockCache.get(STOCK_LIST_CACHE_KEY, Object.class);
    if (cached instanceof List<?> list) {
      return list.stream()
          .filter(StockInfo.class::isInstance)
          .map(StockInfo.class::cast)
          .collect(Collectors.toList());
    }

    List<StockInfo> loaded = stockInfoRepository.findAllListed();
    stockCache.put(STOCK_LIST_CACHE_KEY, loaded);
    return loaded;
  }

  private boolean isListed(StockInfo stockInfo) {
    return stockInfo != null && Boolean.TRUE.equals(stockInfo.getListed());
  }

  private boolean containsIgnoreCase(String source, String keywordLowerCase) {
    return source != null
        && source.toLowerCase(Locale.ROOT).contains(Objects.requireNonNull(keywordLowerCase));
  }

  private KLineVO toKLineVO(KLinePoint p) {
    return new KLineVO(
        p.getDate(),
        p.getOpen(),
        p.getClose(),
        p.getHigh(),
        p.getLow(),
        p.getVolume(),
        p.getAmount());
  }

  private MarketLatencyMetricVO toLatencyMetric(String metricName) {
    Timer timer = meterRegistry.find(metricName).timer();
    if (timer == null) {
      return new MarketLatencyMetricVO(metricName, 0L, null, null, null, null);
    }

    HistogramSnapshot snapshot = timer.takeSnapshot();
    return new MarketLatencyMetricVO(
        metricName,
        timer.count(),
        toNullable(timer.mean(TimeUnit.MILLISECONDS)),
        toNullable(timer.max(TimeUnit.MILLISECONDS)),
        percentileOf(snapshot, 0.95),
        percentileOf(snapshot, 0.99));
  }

  private Double percentileOf(HistogramSnapshot snapshot, double percentile) {
    for (ValueAtPercentile valueAtPercentile : snapshot.percentileValues()) {
      if (Math.abs(valueAtPercentile.percentile() - percentile) < 0.0001d) {
        return toNullable(valueAtPercentile.value(TimeUnit.MILLISECONDS));
      }
    }
    return null;
  }

  private Double toNullable(double value) {
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      return null;
    }
    return value;
  }

}
