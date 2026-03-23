package com.lzbsdsg.stocksimulation.market.application;

import com.lzbsdsg.stocksimulation.common.exception.BizException;
import com.lzbsdsg.stocksimulation.common.result.ErrorCode;
import com.lzbsdsg.stocksimulation.config.CaffeineConfig;
import com.lzbsdsg.stocksimulation.market.application.vo.KLineVO;
import com.lzbsdsg.stocksimulation.market.application.vo.QuoteVO;
import com.lzbsdsg.stocksimulation.market.domain.entity.KLinePeriod;
import com.lzbsdsg.stocksimulation.market.domain.entity.KLinePoint;
import com.lzbsdsg.stocksimulation.market.domain.entity.QuoteSnapshot;
import com.lzbsdsg.stocksimulation.market.domain.entity.StockInfo;
import com.lzbsdsg.stocksimulation.market.domain.repository.StockInfoRepository;
import com.lzbsdsg.stocksimulation.market.domain.service.MarketDataFacade;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

/** 行情应用服务 */
@Service
@RequiredArgsConstructor
public class MarketApplicationService {

  private static final String STOCK_LIST_CACHE_KEY = "listed-all";
  private static final int SEARCH_LIMIT = 20;

  private final MarketDataFacade marketDataFacade;
  private final StockInfoRepository stockInfoRepository;
  private final CacheManager cacheManager;

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
    List<StockInfo> matchedStocks =
        listedStocks.stream()
            .filter(this::isListed)
            .filter(
                stock ->
                    containsIgnoreCase(stock.getStockCode(), normalizedKeyword)
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
}
