package com.lzbsdsg.stocksimulation.watchlist.application;

import com.lzbsdsg.stocksimulation.common.exception.BizException;
import com.lzbsdsg.stocksimulation.common.result.ErrorCode;
import com.lzbsdsg.stocksimulation.market.domain.entity.QuoteSnapshot;
import com.lzbsdsg.stocksimulation.market.domain.entity.StockInfo;
import com.lzbsdsg.stocksimulation.market.domain.repository.StockInfoRepository;
import com.lzbsdsg.stocksimulation.market.domain.service.MarketDataFacade;
import com.lzbsdsg.stocksimulation.watchlist.application.vo.WatchlistItemVO;
import com.lzbsdsg.stocksimulation.watchlist.domain.entity.WatchlistItem;
import com.lzbsdsg.stocksimulation.watchlist.domain.repository.WatchlistRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 自选股应用服务 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WatchlistApplicationService {

  private static final int WATCHLIST_MAX_SIZE = 50;

  private final WatchlistRepository watchlistRepository;
  private final MarketDataFacade marketDataFacade;
  private final StockInfoRepository stockInfoRepository;

  public List<WatchlistItemVO> getWatchlist() {
    Long userId = currentUserId();
    List<WatchlistItem> items = watchlistRepository.findByUserId(userId);
    if (items.isEmpty()) {
      return List.of();
    }

    List<String> stockCodes = items.stream().map(WatchlistItem::getStockCode).toList();
    Map<String, QuoteSnapshot> quoteMap = new HashMap<>();
    try {
      List<QuoteSnapshot> quotes = marketDataFacade.batchGetQuotes(stockCodes);
      for (QuoteSnapshot quote : quotes) {
        if (quote == null || quote.getStockCode() == null) {
          continue;
        }
        quoteMap.put(normalizeStockCode(quote.getStockCode()), quote);
      }
    } catch (BizException ex) {
      if (ex.getErrorCode() != ErrorCode.MARKET_DATA_UNAVAILABLE
          && ex.getErrorCode() != ErrorCode.MARKET_PROVIDER_ALL_FAILED) {
        throw ex;
      }
      log.warn("watchlist.quote.degraded userId={} size={}", userId, items.size());
    }

    List<WatchlistItemVO> result = new ArrayList<>(items.size());
    for (WatchlistItem item : items) {
      QuoteSnapshot quote = quoteMap.get(normalizeStockCode(item.getStockCode()));
      result.add(
          new WatchlistItemVO(
              normalizeStockCode(item.getStockCode()),
              quote != null && quote.getStockName() != null
                  ? quote.getStockName()
                  : item.getStockName(),
              quote == null ? null : quote.getCurrentPrice(),
              quote == null ? null : quote.getChangePercent(),
              item.getSortOrder()));
    }
    return result;
  }

  @Transactional
  public void addStock(String stockCode) {
    Long userId = currentUserId();
    String normalizedCode = normalizeStockCode(stockCode);
    if (normalizedCode.isBlank()) {
      throw new BizException(ErrorCode.BAD_REQUEST, "stockCode不能为空");
    }

    if (watchlistRepository.findByUserIdAndStockCode(userId, normalizedCode).isPresent()) {
      throw new BizException(ErrorCode.WATCHLIST_ALREADY_EXISTS);
    }

    long count = watchlistRepository.countByUserId(userId);
    if (count >= WATCHLIST_MAX_SIZE) {
      throw new BizException(ErrorCode.WATCHLIST_LIMIT_EXCEEDED);
    }

    StockInfo stockInfo =
        stockInfoRepository
            .findByStockCode(normalizedCode)
            .filter(item -> Boolean.TRUE.equals(item.getListed()))
            .orElseThrow(() -> new BizException(ErrorCode.MARKET_STOCK_NOT_FOUND));

    String stockName = stockInfo.getStockName();
    try {
      QuoteSnapshot quote = marketDataFacade.getQuote(normalizedCode);
      if (quote != null && quote.getStockName() != null && !quote.getStockName().isBlank()) {
        stockName = quote.getStockName();
      }
    } catch (BizException ex) {
      if (ex.getErrorCode() != ErrorCode.MARKET_DATA_UNAVAILABLE
          && ex.getErrorCode() != ErrorCode.MARKET_PROVIDER_ALL_FAILED
          && ex.getErrorCode() != ErrorCode.MARKET_STOCK_NOT_FOUND) {
        throw ex;
      }
      log.warn("watchlist.add.quote.degraded userId={} stockCode={}", userId, normalizedCode);
    }

    WatchlistItem item = new WatchlistItem();
    item.setUserId(userId);
    item.setStockCode(normalizedCode);
    item.setStockName(stockName);
    item.setSortOrder((int) count + 1);
    item.setCreatedAt(LocalDateTime.now());
    watchlistRepository.save(item);
  }

  @Transactional
  public void removeStock(String stockCode) {
    Long userId = currentUserId();
    String normalizedCode = normalizeStockCode(stockCode);
    watchlistRepository.deleteByUserIdAndStockCode(userId, normalizedCode);
  }

  @Transactional
  public void updateSort(List<String> stockCodes) {
    Long userId = currentUserId();
    if (stockCodes == null || stockCodes.isEmpty()) {
      return;
    }

    List<WatchlistItem> existing = watchlistRepository.findByUserId(userId);
    if (existing.isEmpty()) {
      return;
    }

    Map<String, WatchlistItem> existingMap = new HashMap<>(existing.size());
    for (WatchlistItem item : existing) {
      existingMap.put(normalizeStockCode(item.getStockCode()), item);
    }

    Set<String> uniqueInputCodes =
        stockCodes.stream()
            .map(this::normalizeStockCode)
            .filter(code -> !code.isBlank())
            .collect(Collectors.toCollection(LinkedHashSet::new));

    if (uniqueInputCodes.size() != existingMap.size()
        || !existingMap.keySet().containsAll(uniqueInputCodes)) {
      throw new BizException(ErrorCode.BAD_REQUEST, "排序股票列表与当前自选股不一致");
    }

    int sort = 1;
    List<WatchlistItem> updatedItems = new ArrayList<>(existing.size());
    for (String code : uniqueInputCodes) {
      WatchlistItem item = existingMap.get(code);
      item.setSortOrder(sort++);
      updatedItems.add(item);
    }
    watchlistRepository.batchUpdateSort(userId, updatedItems);
  }

  private Long currentUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || authentication instanceof AnonymousAuthenticationToken
        || authentication.getPrincipal() == null) {
      throw new BizException(ErrorCode.UNAUTHORIZED);
    }
    try {
      return Long.parseLong(String.valueOf(authentication.getPrincipal()));
    } catch (NumberFormatException ex) {
      throw new BizException(ErrorCode.UNAUTHORIZED);
    }
  }

  private String normalizeStockCode(String stockCode) {
    if (stockCode == null) {
      return "";
    }
    return stockCode.trim().toLowerCase(Locale.ROOT);
  }
}
