package com.lzbsdsg.stocksimulation.portfolio.domain.service;

import com.lzbsdsg.stocksimulation.common.exception.BizException;
import com.lzbsdsg.stocksimulation.common.result.ErrorCode;
import com.lzbsdsg.stocksimulation.market.domain.entity.QuoteSnapshot;
import com.lzbsdsg.stocksimulation.market.domain.service.MarketDataFacade;
import com.lzbsdsg.stocksimulation.portfolio.domain.entity.AssetSnapshot;
import com.lzbsdsg.stocksimulation.portfolio.domain.entity.Position;
import com.lzbsdsg.stocksimulation.portfolio.domain.repository.AssetSnapshotRepository;
import com.lzbsdsg.stocksimulation.portfolio.domain.repository.PositionRepository;
import com.lzbsdsg.stocksimulation.user.domain.entity.Account;
import com.lzbsdsg.stocksimulation.user.domain.repository.AccountRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 资产快照服务（领域服务）
 *
 * <p>负责每日收盘后拍快照，计算当日收益与累计收益率。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetSnapshotService {

  private static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");

  private final AccountRepository accountRepository;
  private final PositionRepository positionRepository;
  private final AssetSnapshotRepository assetSnapshotRepository;
  private final MarketDataFacade marketDataFacade;

  /**
   * 分批创建每日资产快照。
   *
   * <p>由调度器在收盘后调用。批内使用 batchGetQuotes 降低行情调用开销。
   */
  public SnapshotBatchResult createDailySnapshots(LocalDate snapshotDate, List<Long> userIds) {
    if (userIds == null || userIds.isEmpty()) {
      return new SnapshotBatchResult(0, 0, 0);
    }

    Map<Long, List<Position>> positionsByUser = new HashMap<>(userIds.size());
    for (Long userId : userIds) {
      positionsByUser.put(userId, positionRepository.findByUserId(userId));
    }
    Map<String, QuoteSnapshot> quoteMap = loadQuoteMap(positionsByUser);

    int created = 0;
    int skipped = 0;
    int failed = 0;
    for (Long userId : userIds) {
      SnapshotCreateStatus status =
          createDailySnapshot(
              snapshotDate, userId, positionsByUser.getOrDefault(userId, List.of()), quoteMap);
      switch (status) {
        case CREATED -> created++;
        case SKIPPED -> skipped++;
        case FAILED -> failed++;
      }
    }
    return new SnapshotBatchResult(created, skipped, failed);
  }

  /** 执行 T+1 到期持仓解冻。 */
  public int unfreezeDuePositions(LocalDate today) {
    return positionRepository.unfreezeDuePositions(today);
  }

  private SnapshotCreateStatus createDailySnapshot(
      LocalDate snapshotDate,
      Long userId,
      List<Position> positions,
      Map<String, QuoteSnapshot> quoteMap) {
    if (assetSnapshotRepository.findByUserIdAndDate(userId, snapshotDate).isPresent()) {
      return SnapshotCreateStatus.SKIPPED;
    }

    Optional<Account> accountOptional = accountRepository.findByUserId(userId);
    if (accountOptional.isEmpty()) {
      log.warn("portfolio.snapshot.skip_account_missing userId={} date={}", userId, snapshotDate);
      return SnapshotCreateStatus.SKIPPED;
    }
    Account account = accountOptional.get();

    BigDecimal marketValue = calcMarketValue(positions, quoteMap);
    BigDecimal available = defaultMoney(account.getAvailableBalance());
    BigDecimal frozen = defaultMoney(account.getFrozenBalance());
    BigDecimal initial = defaultMoney(account.getInitialBalance());
    BigDecimal totalAssets =
        available.add(frozen).add(marketValue).setScale(2, RoundingMode.HALF_UP);

    Optional<AssetSnapshot> prevSnapshot =
        assetSnapshotRepository.findLatestBefore(userId, snapshotDate);
    BigDecimal dailyProfit =
        prevSnapshot
            .map(snapshot -> totalAssets.subtract(defaultMoney(snapshot.getTotalAssets())))
            .orElse(totalAssets.subtract(initial))
            .setScale(2, RoundingMode.HALF_UP);
    BigDecimal cumulativeProfitRate = safePercent(totalAssets.subtract(initial), initial);

    AssetSnapshot snapshot = new AssetSnapshot();
    snapshot.setUserId(userId);
    snapshot.setSnapshotDate(snapshotDate);
    snapshot.setAvailableBalance(available);
    snapshot.setMarketValue(marketValue);
    snapshot.setTotalAssets(totalAssets);
    snapshot.setDailyProfit(dailyProfit);
    snapshot.setCumulativeProfitRate(cumulativeProfitRate);
    snapshot.setCreatedAt(LocalDateTime.now(ZONE_SHANGHAI));

    try {
      assetSnapshotRepository.save(snapshot);
      return SnapshotCreateStatus.CREATED;
    } catch (DataIntegrityViolationException ex) {
      log.info("portfolio.snapshot.idempotent_skip userId={} date={}", userId, snapshotDate);
      return SnapshotCreateStatus.SKIPPED;
    } catch (RuntimeException ex) {
      log.error("portfolio.snapshot.create_failed userId={} date={}", userId, snapshotDate, ex);
      return SnapshotCreateStatus.FAILED;
    }
  }

  private Map<String, QuoteSnapshot> loadQuoteMap(Map<Long, List<Position>> positionsByUser) {
    List<String> codes =
        positionsByUser.values().stream()
            .flatMap(List::stream)
            .map(Position::getStockCode)
            .filter(code -> code != null && !code.isBlank())
            .map(this::normalizeStockCode)
            .distinct()
            .toList();
    if (codes.isEmpty()) {
      return Collections.emptyMap();
    }

    try {
      List<QuoteSnapshot> quotes = marketDataFacade.batchGetQuotes(codes);
      Map<String, QuoteSnapshot> quoteMap = new HashMap<>(quotes.size());
      for (QuoteSnapshot quote : quotes) {
        if (quote == null || quote.getStockCode() == null || quote.getStockCode().isBlank()) {
          continue;
        }
        quoteMap.put(normalizeStockCode(quote.getStockCode()), quote);
      }
      return quoteMap;
    } catch (BizException ex) {
      if (ex.getErrorCode() == ErrorCode.MARKET_DATA_UNAVAILABLE) {
        log.warn("portfolio.snapshot.quote_unavailable fallback_to_cost codes={}", codes.size());
        return Collections.emptyMap();
      }
      throw ex;
    }
  }

  private BigDecimal calcMarketValue(
      List<Position> positions, Map<String, QuoteSnapshot> quoteMap) {
    BigDecimal marketValue = BigDecimal.ZERO;
    for (Position position : positions) {
      int quantity = position.getTotalQuantity() == null ? 0 : position.getTotalQuantity();
      if (quantity <= 0) {
        continue;
      }
      QuoteSnapshot quote = quoteMap.get(normalizeStockCode(position.getStockCode()));
      BigDecimal closePrice = resolveClosePrice(position, quote);
      marketValue = marketValue.add(closePrice.multiply(BigDecimal.valueOf(quantity)));
    }
    return marketValue.setScale(2, RoundingMode.HALF_UP);
  }

  private BigDecimal resolveClosePrice(Position position, QuoteSnapshot quote) {
    if (quote != null && quote.getClosePrice() != null) {
      return quote.getClosePrice().setScale(4, RoundingMode.HALF_UP);
    }
    if (quote != null && quote.getCurrentPrice() != null) {
      return quote.getCurrentPrice().setScale(4, RoundingMode.HALF_UP);
    }
    return defaultPrice(position.getCostPrice());
  }

  private BigDecimal safePercent(BigDecimal numerator, BigDecimal denominator) {
    if (denominator == null || denominator.compareTo(BigDecimal.ZERO) <= 0) {
      return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    }
    return numerator
        .divide(denominator, 6, RoundingMode.HALF_UP)
        .multiply(BigDecimal.valueOf(100))
        .setScale(4, RoundingMode.HALF_UP);
  }

  private BigDecimal defaultMoney(BigDecimal value) {
    if (value == null) {
      return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
    return value.setScale(2, RoundingMode.HALF_UP);
  }

  private BigDecimal defaultPrice(BigDecimal value) {
    if (value == null) {
      return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    }
    return value.setScale(4, RoundingMode.HALF_UP);
  }

  private String normalizeStockCode(String stockCode) {
    if (stockCode == null) {
      return "";
    }
    return stockCode.trim().toLowerCase(Locale.ROOT);
  }

  public record SnapshotBatchResult(int created, int skipped, int failed) {}

  private enum SnapshotCreateStatus {
    CREATED,
    SKIPPED,
    FAILED
  }
}
