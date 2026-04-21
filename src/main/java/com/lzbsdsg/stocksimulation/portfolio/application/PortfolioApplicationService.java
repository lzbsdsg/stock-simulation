package com.lzbsdsg.stocksimulation.portfolio.application;

import com.lzbsdsg.stocksimulation.common.annotation.ReadOnly;
import com.lzbsdsg.stocksimulation.common.exception.BizException;
import com.lzbsdsg.stocksimulation.common.result.ErrorCode;
import com.lzbsdsg.stocksimulation.common.result.PageResult;
import com.lzbsdsg.stocksimulation.market.domain.entity.QuoteSnapshot;
import com.lzbsdsg.stocksimulation.market.domain.service.MarketDataFacade;
import com.lzbsdsg.stocksimulation.portfolio.application.vo.EquityCurvePointVO;
import com.lzbsdsg.stocksimulation.portfolio.application.vo.EquityCurveVO;
import com.lzbsdsg.stocksimulation.portfolio.application.vo.FundFlowVO;
import com.lzbsdsg.stocksimulation.portfolio.application.vo.OverviewVO;
import com.lzbsdsg.stocksimulation.portfolio.application.vo.PositionVO;
import com.lzbsdsg.stocksimulation.portfolio.domain.entity.AssetSnapshot;
import com.lzbsdsg.stocksimulation.portfolio.domain.entity.FundFlow;
import com.lzbsdsg.stocksimulation.portfolio.domain.entity.Position;
import com.lzbsdsg.stocksimulation.portfolio.domain.repository.AssetSnapshotRepository;
import com.lzbsdsg.stocksimulation.portfolio.domain.repository.FundFlowRepository;
import com.lzbsdsg.stocksimulation.portfolio.domain.repository.PositionRepository;
import com.lzbsdsg.stocksimulation.portfolio.domain.service.PositionDomainService;
import com.lzbsdsg.stocksimulation.user.domain.entity.Account;
import com.lzbsdsg.stocksimulation.user.domain.repository.AccountRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** 持仓与资产应用服务 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioApplicationService {

  private static final int MIN_PAGE = 1;
  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 200;
  private static final int OVERVIEW_QUOTE_LIMIT = 500;
  private static final int DEFAULT_EQUITY_DAYS = 30;
  private static final int MAX_EQUITY_DAYS = 365;
  private static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");

  private final PositionRepository positionRepository;
  private final AccountRepository accountRepository;
  private final FundFlowRepository fundFlowRepository;
  private final AssetSnapshotRepository assetSnapshotRepository;
  private final MarketDataFacade marketDataFacade;

  private final PositionDomainService positionDomainService = new PositionDomainService();

  /**
   * 获取资产总览
   *
   * <p>总资产 = 可用资金 + 冻结资金 + 持仓市值
   */
  public OverviewVO getOverview() {
    Long userId = currentUserId();
    Account account =
        accountRepository
            .findByUserId(userId)
            .orElseThrow(() -> new BizException(ErrorCode.USER_ACCOUNT_NOT_FOUND));

    long positionCount = positionRepository.countByUserId(userId);
    BigDecimal marketValue;
    if (positionCount == 0) {
      marketValue = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    } else if (positionCount > OVERVIEW_QUOTE_LIMIT) {
      marketValue =
          defaultMoney(positionRepository.sumCostMarketValueByUserId(userId));
      log.warn(
          "portfolio.overview.aggregate_fallback position_count={} quote_limit={}",
          positionCount,
          OVERVIEW_QUOTE_LIMIT);
    } else {
      List<Position> positions = positionRepository.findByUserId(userId);
      Map<String, QuoteSnapshot> quoteMap = loadQuoteMap(positions, OVERVIEW_QUOTE_LIMIT, true);
      marketValue = calcMarketValue(positions, quoteMap, false);
    }

    BigDecimal available = defaultMoney(account.getAvailableBalance());
    BigDecimal frozen = defaultMoney(account.getFrozenBalance());
    BigDecimal initial = defaultMoney(account.getInitialBalance());
    BigDecimal totalAssets = available.add(frozen).add(marketValue).setScale(2, RoundingMode.HALF_UP);
    BigDecimal totalProfit = totalAssets.subtract(initial).setScale(2, RoundingMode.HALF_UP);
    BigDecimal totalProfitRate = safePercent(totalProfit, initial);

    LocalDate today = LocalDate.now(ZONE_SHANGHAI);
    Optional<AssetSnapshot> previousSnapshot = assetSnapshotRepository.findLatestBefore(userId, today);
    BigDecimal baseline =
        previousSnapshot.map(AssetSnapshot::getTotalAssets).orElse(initial);
    BigDecimal todayProfit = totalAssets.subtract(defaultMoney(baseline)).setScale(2, RoundingMode.HALF_UP);
    BigDecimal todayProfitRate = safePercent(todayProfit, defaultMoney(baseline));

    return new OverviewVO(
        totalAssets,
        available,
        frozen,
        marketValue,
        initial,
        totalProfit,
        totalProfitRate,
        todayProfit,
        todayProfitRate);
  }

  /** 获取持仓列表（含实时盈亏） */
  public PageResult<PositionVO> getPositions(int page, int size) {
    Long userId = currentUserId();
    int safePage = sanitizePage(page);
    int safeSize = sanitizeSize(size);
    List<Position> positions = positionRepository.findByUserId(userId, safePage, safeSize);
    long total = positionRepository.countByUserId(userId);
    if (positions.isEmpty()) {
      return new PageResult<>(List.of(), total, safePage, safeSize);
    }

    Map<String, QuoteSnapshot> quoteMap = loadQuoteMap(positions);
    List<PositionVO> records = positions.stream().map(position -> toPositionVO(position, quoteMap)).toList();
    return new PageResult<>(records, total, safePage, safeSize);
  }

  /** 获取资金流水 */
  @ReadOnly
  public PageResult<FundFlowVO> getFundFlows(int page, int size) {
    Long userId = currentUserId();
    int safePage = sanitizePage(page);
    int safeSize = sanitizeSize(size);
    List<FundFlowVO> records =
        fundFlowRepository.findByUserId(userId, safePage, safeSize).stream()
            .map(this::toFundFlowVO)
            .toList();
    long total = fundFlowRepository.countByUserId(userId);
    return new PageResult<>(records, total, safePage, safeSize);
  }

  /** 获取收益曲线（默认 30 天，最大 365 天）。 */
  @ReadOnly
  public EquityCurveVO getEquityCurve(Integer days) {
    Long userId = currentUserId();
    int safeDays = sanitizeDays(days);
    LocalDate to = LocalDate.now(ZONE_SHANGHAI);
    LocalDate from = to.minusDays(safeDays - 1L);
    List<AssetSnapshot> snapshots = assetSnapshotRepository.findByUserIdBetween(userId, from, to);
    if (snapshots.isEmpty()) {
      return new EquityCurveVO(List.of(), BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP), null);
    }

    BigDecimal peak = null;
    BigDecimal maxDrawdown = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    LocalDate maxDrawdownDate = null;
    List<EquityCurvePointVO> points =
        snapshots.stream()
            .map(
                snapshot ->
                    new EquityCurvePointVO(
                        snapshot.getSnapshotDate(),
                        defaultMoney(snapshot.getTotalAssets()),
                        defaultPercent(snapshot.getCumulativeProfitRate())))
            .toList();

    for (EquityCurvePointVO point : points) {
      if (peak == null || point.totalAssets().compareTo(peak) > 0) {
        peak = point.totalAssets();
      }
      if (peak == null || peak.compareTo(BigDecimal.ZERO) <= 0) {
        continue;
      }
      BigDecimal drawdown =
          peak.subtract(point.totalAssets())
              .divide(peak, 6, RoundingMode.HALF_UP)
              .multiply(BigDecimal.valueOf(100))
              .setScale(4, RoundingMode.HALF_UP);
      if (drawdown.compareTo(maxDrawdown) > 0) {
        maxDrawdown = drawdown;
        maxDrawdownDate = point.date();
      }
    }
    return new EquityCurveVO(points, maxDrawdown, maxDrawdownDate);
  }

  private PositionVO toPositionVO(Position position, Map<String, QuoteSnapshot> quoteMap) {
    QuoteSnapshot quote = quoteMap.get(normalizeStockCode(position.getStockCode()));
    BigDecimal currentPrice = resolveCurrentPrice(position, quote);
    BigDecimal marketValue =
        currentPrice
            .multiply(BigDecimal.valueOf(defaultInt(position.getTotalQuantity())))
            .setScale(2, RoundingMode.HALF_UP);
    BigDecimal profit = positionDomainService.calculateProfit(position, currentPrice).setScale(2, RoundingMode.HALF_UP);
    BigDecimal profitRate = positionDomainService.calculateProfitRate(position, currentPrice).setScale(4, RoundingMode.HALF_UP);

    BigDecimal todayProfit = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    if (quote != null && quote.getClosePrice() != null && position.getTotalQuantity() != null) {
      todayProfit =
          currentPrice
              .subtract(quote.getClosePrice())
              .multiply(BigDecimal.valueOf(position.getTotalQuantity()))
              .setScale(2, RoundingMode.HALF_UP);
    }

    return new PositionVO(
        position.getId(),
        normalizeStockCode(position.getStockCode()),
        position.getStockName(),
        defaultInt(position.getTotalQuantity()),
        defaultInt(position.getAvailableQuantity()),
        defaultInt(position.getFrozenQuantity()),
        defaultPrice(position.getCostPrice()),
        currentPrice,
        marketValue,
        profit,
        profitRate,
        todayProfit,
        position.getFrozenUntil());
  }

  private FundFlowVO toFundFlowVO(FundFlow flow) {
    return new FundFlowVO(
        flow.getId(),
        flow.getFlowType().name(),
        defaultMoney(flow.getAmount()),
        defaultMoney(flow.getBalanceAfter()),
        flow.getOrderId(),
        flow.getRemark(),
        flow.getCreatedAt());
  }

  private BigDecimal calcMarketValue(
      List<Position> positions, Map<String, QuoteSnapshot> quoteMap, boolean preferClosePrice) {
    BigDecimal marketValue = BigDecimal.ZERO;
    for (Position position : positions) {
      int quantity = defaultInt(position.getTotalQuantity());
      if (quantity <= 0) {
        continue;
      }
      QuoteSnapshot quote = quoteMap.get(normalizeStockCode(position.getStockCode()));
      BigDecimal price = preferClosePrice ? resolveClosePrice(position, quote) : resolveCurrentPrice(position, quote);
      marketValue = marketValue.add(price.multiply(BigDecimal.valueOf(quantity)));
    }
    return marketValue.setScale(2, RoundingMode.HALF_UP);
  }

  private Map<String, QuoteSnapshot> loadQuoteMap(List<Position> positions) {
    return loadQuoteMap(positions, Integer.MAX_VALUE, false);
  }

  private Map<String, QuoteSnapshot> loadQuoteMap(
      List<Position> positions, int quoteLimit, boolean allowFallbackByLimit) {
    List<String> stockCodes =
        positions.stream()
            .map(Position::getStockCode)
            .filter(code -> code != null && !code.isBlank())
            .map(this::normalizeStockCode)
            .distinct()
            .toList();
    if (stockCodes.isEmpty()) {
      return Collections.emptyMap();
    }
    if (allowFallbackByLimit && stockCodes.size() > quoteLimit) {
      log.warn(
          "portfolio.quote.skip_large_set code_size={} quote_limit={} fallback_to_cost=true",
          stockCodes.size(),
          quoteLimit);
      return Collections.emptyMap();
    }

    try {
      List<QuoteSnapshot> quotes = marketDataFacade.batchGetQuotes(stockCodes);
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
        log.warn("portfolio.quote.unavailable fallback_to_cost_size={}", stockCodes.size());
        return Collections.emptyMap();
      }
      throw ex;
    }
  }

  private BigDecimal resolveCurrentPrice(Position position, QuoteSnapshot quote) {
    if (quote != null && quote.getCurrentPrice() != null) {
      return quote.getCurrentPrice().setScale(4, RoundingMode.HALF_UP);
    }
    return defaultPrice(position.getCostPrice());
  }

  private BigDecimal resolveClosePrice(Position position, QuoteSnapshot quote) {
    if (quote != null && quote.getClosePrice() != null) {
      return quote.getClosePrice().setScale(4, RoundingMode.HALF_UP);
    }
    return resolveCurrentPrice(position, quote);
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

  private BigDecimal defaultPercent(BigDecimal value) {
    if (value == null) {
      return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    }
    return value.setScale(4, RoundingMode.HALF_UP);
  }

  private BigDecimal defaultPrice(BigDecimal value) {
    if (value == null) {
      return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    }
    return value.setScale(4, RoundingMode.HALF_UP);
  }

  private int defaultInt(Integer value) {
    return value == null ? 0 : value;
  }

  private int sanitizePage(int page) {
    return Math.max(page, MIN_PAGE);
  }

  private int sanitizeSize(int size) {
    if (size <= 0) {
      return DEFAULT_PAGE_SIZE;
    }
    return Math.min(size, MAX_PAGE_SIZE);
  }

  private int sanitizeDays(Integer days) {
    if (days == null || days <= 0) {
      return DEFAULT_EQUITY_DAYS;
    }
    return Math.min(days, MAX_EQUITY_DAYS);
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
