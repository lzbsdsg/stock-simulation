package com.lzbsdsg.stocksimulation.trade.application;

import com.lzbsdsg.stocksimulation.common.annotation.ReadOnly;
import com.lzbsdsg.stocksimulation.common.exception.BizException;
import com.lzbsdsg.stocksimulation.common.result.ErrorCode;
import com.lzbsdsg.stocksimulation.common.result.PageResult;
import com.lzbsdsg.stocksimulation.config.CaffeineConfig;
import com.lzbsdsg.stocksimulation.config.TradeRuleConfig;
import com.lzbsdsg.stocksimulation.market.domain.entity.QuoteSnapshot;
import com.lzbsdsg.stocksimulation.market.domain.repository.StockInfoRepository;
import com.lzbsdsg.stocksimulation.market.domain.service.MarketDataFacade;
import com.lzbsdsg.stocksimulation.portfolio.domain.entity.FundFlow;
import com.lzbsdsg.stocksimulation.portfolio.domain.entity.Position;
import com.lzbsdsg.stocksimulation.portfolio.domain.repository.FundFlowRepository;
import com.lzbsdsg.stocksimulation.portfolio.domain.repository.PositionRepository;
import com.lzbsdsg.stocksimulation.portfolio.domain.service.PositionDomainService;
import com.lzbsdsg.stocksimulation.trade.application.command.CancelOrderCommand;
import com.lzbsdsg.stocksimulation.trade.application.command.PlaceOrderCommand;
import com.lzbsdsg.stocksimulation.trade.application.vo.OrderVO;
import com.lzbsdsg.stocksimulation.trade.application.vo.TradeVO;
import com.lzbsdsg.stocksimulation.trade.domain.entity.Order;
import com.lzbsdsg.stocksimulation.trade.domain.entity.OrderSide;
import com.lzbsdsg.stocksimulation.trade.domain.entity.OrderStatus;
import com.lzbsdsg.stocksimulation.trade.domain.entity.OrderType;
import com.lzbsdsg.stocksimulation.trade.domain.entity.Trade;
import com.lzbsdsg.stocksimulation.trade.domain.repository.OrderRepository;
import com.lzbsdsg.stocksimulation.trade.domain.repository.TradeRepository;
import com.lzbsdsg.stocksimulation.trade.domain.service.FeeCalculator;
import com.lzbsdsg.stocksimulation.trade.domain.service.MatchEngine;
import com.lzbsdsg.stocksimulation.trade.domain.service.OrderDomainService;
import com.lzbsdsg.stocksimulation.trade.infrastructure.gateway.IdempotencyGateway;
import com.lzbsdsg.stocksimulation.trade.infrastructure.mq.OrderMessageProducer;
import com.lzbsdsg.stocksimulation.trade.infrastructure.mq.TradeFilledEvent;
import com.lzbsdsg.stocksimulation.user.application.AccountApplicationService;
import com.lzbsdsg.stocksimulation.user.domain.entity.Account;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 交易应用服务
 *
 * <p>编排下单/撤单/查询流程，不包含业务规则。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradeApplicationService {

  private static final int MIN_PAGE = 1;
  private static final int DEFAULT_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 200;
  private static final int DEFAULT_ARCHIVE_BATCH_SIZE = 500;
  private static final int MIN_ARCHIVE_RETAIN_DAYS = 1;
  private static final long TX_TARGET_MS = 50L;
  private static final String TRADE_WINDOW_CACHE_KEY = "trade:window";
  private static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");

  private final OrderRepository orderRepository;
  private final TradeRepository tradeRepository;
  private final IdempotencyGateway idempotencyGateway;
  private final OrderMessageProducer orderMessageProducer;
  private final MarketDataFacade marketDataFacade;
  private final StockInfoRepository stockInfoRepository;
  private final AccountApplicationService accountApplicationService;
  private final FundFlowRepository fundFlowRepository;
  private final PositionRepository positionRepository;
  private final CacheManager cacheManager;
  private final TradeRuleConfig tradeRuleConfig;
  private final MeterRegistry meterRegistry;

  private final OrderDomainService orderDomainService = new OrderDomainService();
  private final FeeCalculator feeCalculator = new FeeCalculator();
  private final MatchEngine matchEngine = new MatchEngine();
  private final PositionDomainService positionDomainService = new PositionDomainService();
  private Counter tradeOrderCreatedCounter;
  private Counter tradeOrderFilledCounter;
  private Timer tradeMatchDurationTimer;

  @PostConstruct
  void initMetrics() {
    tradeOrderCreatedCounter = Counter.builder("trade_order_created_total").register(meterRegistry);
    tradeOrderFilledCounter = Counter.builder("trade_order_filled_total").register(meterRegistry);
    tradeMatchDurationTimer =
        Timer.builder("trade_match_duration_seconds")
            .description("Duration of single order match flow")
            .publishPercentiles(0.95, 0.99)
            .publishPercentileHistogram()
            .register(meterRegistry);
  }

  private void ensureMetricsInitialized() {
    if (tradeOrderCreatedCounter != null
        && tradeOrderFilledCounter != null
        && tradeMatchDurationTimer != null) {
      return;
    }
    initMetrics();
  }

  public enum MatchResult {
    MATCHED,
    SKIPPED_NOT_FOUND,
    SKIPPED_ALREADY_DONE,
    SKIPPED_PRICE_NOT_MATCHED
  }

  /**
   * 下单（买入/卖出）
   *
   * <p>流程： 1. 幂等校验（clientOrderId） 2. 校验交易时间 & 涨跌停 & 最小单位 3. 计算冻结金额 / 校验可卖数量 4. SELECT FOR UPDATE
   * 锁定账户 5. 扣减可用资金/冻结持仓 6. 插入 Order(PENDING) 7. 发送撮合消息到 MQ
   */
  @Transactional
  public OrderVO placeOrder(PlaceOrderCommand command) {
    ensureMetricsInitialized();
    long startNano = System.nanoTime();
    Long userId = currentUserId();
    String clientOrderId = command.clientOrderId();

    if (!idempotencyGateway.tryAcquire(clientOrderId)) {
      throw new BizException(ErrorCode.TRADE_ORDER_DUPLICATE);
    }

    try {
      OrderSide side = parseOrderSide(command.side());
      OrderType orderType = parseOrderType(command.orderType());
      validateTradingWindow();
      validateQuantity(command.quantity());

      QuoteSnapshot quote = marketDataFacade.getQuote(command.stockCode());
      BigDecimal price = resolveOrderPrice(command, orderType, quote);
      if (!orderDomainService.isPriceWithinLimit(price, quote)) {
        throw new BizException(ErrorCode.TRADE_ORDER_PRICE_LIMIT);
      }

      Order order = buildPendingOrder(userId, command, side, orderType, price, quote);
      if (side == OrderSide.BUY) {
        BigDecimal freezeAmount =
            orderDomainService.calculateFreezeAmount(
                price, command.quantity(), feeCalculator.estimateBuyCommissionRate());
        Account accountAfterFreeze =
            accountApplicationService.freezeBalanceAndGetAccount(userId, freezeAmount);
        order.setFrozenAmount(freezeAmount);
        orderRepository.save(order);
        tradeOrderCreatedCounter.increment();
        recordFundFlow(
            userId,
            FundFlow.FundFlowType.FREEZE,
            freezeAmount.negate(),
            accountAfterFreeze.getAvailableBalance(),
            order.getId(),
            "BUY order freeze");
      } else {
        freezeSellPosition(userId, order.getStockCode(), command.quantity());
        order.setFrozenAmount(BigDecimal.ZERO);
        orderRepository.save(order);
        tradeOrderCreatedCounter.increment();
      }

      publishMatchMessageAfterCommit(order.getId());
      logTransactionCost("placeOrder", userId, order.getId(), startNano);
      return toOrderVO(order);
    } catch (RuntimeException ex) {
      // 下单失败时释放幂等键，允许用户修正参数后使用同一个 clientOrderId 重提。
      idempotencyGateway.release(clientOrderId);
      throw ex;
    }
  }

  /** 撤单 */
  @Transactional
  public void cancelOrder(CancelOrderCommand command) {
    cancelOrder(command.orderId());
  }

  /** 撤单（按订单ID） */
  @Transactional
  public void cancelOrder(Long orderId) {
    long startNano = System.nanoTime();
    Long userId = currentUserId();
    try {
      Order order =
          orderRepository
              .findById(orderId)
              .orElseThrow(() -> new BizException(ErrorCode.TRADE_ORDER_NOT_FOUND));
      if (!userId.equals(order.getUserId())) {
        throw new BizException(ErrorCode.TRADE_ORDER_NOT_OWN);
      }
      validateOrderCancelableSnapshot(order);
      if (!order.isCancellable()) {
        throw new BizException(ErrorCode.TRADE_ORDER_CANNOT_CANCEL);
      }

      int remainingQuantity = Math.max(order.remainingQuantity(), 0);
      order.cancel();
      if (!orderRepository.updateWithVersion(order)) {
        throw new BizException(ErrorCode.TRADE_OPTIMISTIC_LOCK_CONFLICT);
      }

      if (order.getSide() == OrderSide.BUY && hasPositiveAmount(order.getFrozenAmount())) {
        Account accountAfterUnfreeze =
            accountApplicationService.unfreezeBalanceAndGetAccount(userId, order.getFrozenAmount());
        recordFundFlow(
            userId,
            FundFlow.FundFlowType.UNFREEZE,
            order.getFrozenAmount(),
            accountAfterUnfreeze.getAvailableBalance(),
            order.getId(),
            "Cancel BUY order");
      }
      if (order.getSide() == OrderSide.SELL && remainingQuantity > 0) {
        unfreezeSellPosition(userId, order.getStockCode(), remainingQuantity);
      }

      logTransactionCost("cancelOrder", userId, order.getId(), startNano);
    } catch (BizException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      log.error("trade.cancelOrder.unexpected userId={} orderId={}", userId, orderId, ex);
      throw new BizException(ErrorCode.TRADE_OPTIMISTIC_LOCK_CONFLICT);
    }
  }

  private void validateOrderCancelableSnapshot(Order order) {
    if (order.getStatus() == null || order.getSide() == null || order.getQuantity() == null) {
      throw new BizException(ErrorCode.TRADE_ORDER_CANNOT_CANCEL);
    }
    if (order.getSide() == OrderSide.SELL
        && (order.getStockCode() == null || order.getStockCode().isBlank())) {
      throw new BizException(ErrorCode.TRADE_ORDER_CANNOT_CANCEL);
    }
  }

  /** 查询当日委托 */
  @ReadOnly
  public PageResult<OrderVO> getTodayOrders(int page, int size) {
    return getOrders("today", page, size);
  }

  /** 查询历史委托 */
  @ReadOnly
  public PageResult<OrderVO> getHistoryOrders(int page, int size) {
    return getOrders("history", page, size);
  }

  /** 查询委托列表 */
  @ReadOnly
  public PageResult<OrderVO> getOrders(String scope, int page, int size) {
    Long userId = currentUserId();
    int safePage = sanitizePage(page);
    int safeSize = sanitizeSize(size);
    LocalDateTime now = LocalDateTime.now(ZONE_SHANGHAI);
    LocalDateTime startOfToday = now.toLocalDate().atStartOfDay();

    String normalizedScope = scope == null ? "today" : scope.trim().toLowerCase(Locale.ROOT);
    if ("today".equals(normalizedScope)) {
      List<OrderVO> records =
          orderRepository
              .findActiveByUserIdAndCreatedAtBetween(userId, startOfToday, now, safePage, safeSize)
              .stream()
              .map(this::toOrderVO)
              .toList();
      long total =
          orderRepository.countActiveByUserIdAndCreatedAtBetween(userId, startOfToday, now);
      return new PageResult<>(records, total, safePage, safeSize);
    }

    LocalDateTime from;
    LocalDateTime to;
    switch (normalizedScope) {
      case "history" -> {
        from = LocalDate.of(1970, 1, 1).atStartOfDay();
        to = startOfToday.minusNanos(1);
      }
      case "all" -> {
        from = LocalDate.of(1970, 1, 1).atStartOfDay();
        to = now;
      }
      default -> {
        from = startOfToday;
        to = now;
      }
    }

    List<OrderVO> records =
        orderRepository
            .findByUserIdAndCreatedAtBetween(userId, from, to, safePage, safeSize)
            .stream()
            .map(this::toOrderVO)
            .toList();
    long total = orderRepository.countByUserIdAndCreatedAtBetween(userId, from, to);
    return new PageResult<>(records, total, safePage, safeSize);
  }

  /** 查询成交记录 */
  @ReadOnly
  public PageResult<TradeVO> getTrades(int page, int size) {
    Long userId = currentUserId();
    int safePage = sanitizePage(page);
    int safeSize = sanitizeSize(size);
    List<TradeVO> records =
        tradeRepository.findByUserId(userId, safePage, safeSize).stream()
            .map(this::toTradeVO)
            .toList();
    long total = tradeRepository.countByUserId(userId);
    return new PageResult<>(records, total, safePage, safeSize);
  }

  /** 撮合单笔订单（由 MQ Consumer 调用） */
  @Transactional
  public MatchResult matchOrder(Long orderId) {
    ensureMetricsInitialized();
    long startNano = System.nanoTime();
    try {
      Order order = orderRepository.findById(orderId).orElse(null);
      if (order == null) {
        log.warn("trade.match.skip_not_found orderId={}", orderId);
        return MatchResult.SKIPPED_NOT_FOUND;
      }
      if (order.getStatus() != OrderStatus.PENDING
          && order.getStatus() != OrderStatus.PARTIAL_FILLED) {
        log.debug("trade.match.idempotent_skip orderId={} status={}", orderId, order.getStatus());
        return MatchResult.SKIPPED_ALREADY_DONE;
      }

      QuoteSnapshot quote = marketDataFacade.getQuote(order.getStockCode());
      if (quote.getCurrentPrice() == null) {
        throw new BizException(ErrorCode.MARKET_DATA_UNAVAILABLE);
      }
      BigDecimal marketPrice = quote.getCurrentPrice();
      if (!orderDomainService.canMatch(order, marketPrice)) {
        return MatchResult.SKIPPED_PRICE_NOT_MATCHED;
      }

      int matchQuantity = order.remainingQuantity();
      if (matchQuantity <= 0) {
        return MatchResult.SKIPPED_ALREADY_DONE;
      }

      BigDecimal tradeAmount = marketPrice.multiply(BigDecimal.valueOf(matchQuantity));
      BigDecimal fee =
          order.getSide() == OrderSide.BUY
              ? feeCalculator.calculateBuyFee(tradeAmount)
              : feeCalculator.calculateSellFee(tradeAmount);

      Trade trade = matchEngine.tryMatch(order, marketPrice, fee);
      if (trade == null) {
        return MatchResult.SKIPPED_PRICE_NOT_MATCHED;
      }

      if (!orderRepository.updateWithVersion(order)) {
        throw new BizException(ErrorCode.TRADE_OPTIMISTIC_LOCK_CONFLICT);
      }

      tradeRepository.save(trade);
      if (order.getSide() == OrderSide.BUY) {
        settleBuyMatch(order, trade);
      } else {
        settleSellMatch(order, trade);
      }
      publishTradeFilledEventAfterCommit(order, trade);
      tradeOrderFilledCounter.increment();

      log.debug(
          "trade.match.ok orderId={} tradeId={} userId={} side={} price={} qty={}",
          order.getId(),
          trade.getId(),
          order.getUserId(),
          order.getSide(),
          trade.getTradePrice(),
          trade.getTradeQuantity());
      return MatchResult.MATCHED;
    } finally {
      tradeMatchDurationTimer.record(
          System.nanoTime() - startNano, java.util.concurrent.TimeUnit.NANOSECONDS);
    }
  }

  /** 收盘后批量过期待成交订单（每次最多处理 batchSize 条） */
  @Transactional
  public int expirePendingOrdersAtClose(int batchSize) {
    int safeBatchSize = batchSize <= 0 ? 200 : batchSize;
    List<Order> pendingOrders = orderRepository.findPendingOrders();
    int processed = 0;

    for (Order order : pendingOrders) {
      if (processed >= safeBatchSize) {
        break;
      }
      if (order.getStatus() != OrderStatus.PENDING
          && order.getStatus() != OrderStatus.PARTIAL_FILLED) {
        continue;
      }
      order.setStatus(OrderStatus.EXPIRED);
      if (!orderRepository.updateWithVersion(order)) {
        continue;
      }

      int remainingQuantity = Math.max(order.remainingQuantity(), 0);
      if (order.getSide() == OrderSide.BUY && hasPositiveAmount(order.getFrozenAmount())) {
        Account accountAfterUnfreeze =
            accountApplicationService.unfreezeBalanceAndGetAccount(
                order.getUserId(), order.getFrozenAmount());
        recordFundFlow(
            order.getUserId(),
            FundFlow.FundFlowType.UNFREEZE,
            order.getFrozenAmount(),
            accountAfterUnfreeze.getAvailableBalance(),
            order.getId(),
            "Close expire BUY order");
      } else if (order.getSide() == OrderSide.SELL && remainingQuantity > 0) {
        unfreezeSellPosition(order.getUserId(), order.getStockCode(), remainingQuantity);
      }
      processed++;
    }
    return processed;
  }

  /** 收盘后修正当日买入持仓 T+1 冻结日期。 */
  @Transactional
  public int markTodayBuyPositionsFrozenUntil() {
    LocalDate today = LocalDate.now(ZONE_SHANGHAI);
    LocalDate nextTradingDate = positionDomainService.nextTradingDate(today);
    return positionRepository.markTodayBoughtPositionsFrozenUntil(today, nextTradingDate);
  }

  /** 归档已完结且无成交明细的历史订单，返回本批次归档数量。 */
  @Transactional
  public int archiveClosedOrders(int retainDays, int batchSize) {
    int safeRetainDays = Math.max(retainDays, MIN_ARCHIVE_RETAIN_DAYS);
    int safeBatchSize = batchSize <= 0 ? DEFAULT_ARCHIVE_BATCH_SIZE : batchSize;
    LocalDateTime cutoff = LocalDate.now(ZONE_SHANGHAI).minusDays(safeRetainDays).atStartOfDay();
    return orderRepository.archiveClosedOrdersWithoutTrades(cutoff, safeBatchSize);
  }

  private void validateTradingWindow() {
    TradingWindow window = loadTradingWindow();
    LocalTime now = LocalTime.now(ZONE_SHANGHAI);
    if (!orderDomainService.isWithinTradingHours(
        now,
        window.morningOpen(),
        window.morningClose(),
        window.afternoonOpen(),
        window.afternoonClose())) {
      throw new BizException(ErrorCode.TRADE_ORDER_MARKET_CLOSED);
    }
  }

  private void validateQuantity(Integer quantity) {
    if (quantity == null || !orderDomainService.isValidQuantity(quantity)) {
      throw new BizException(ErrorCode.TRADE_ORDER_QUANTITY_INVALID);
    }
  }

  private BigDecimal resolveOrderPrice(
      PlaceOrderCommand command, OrderType orderType, QuoteSnapshot quote) {
    if (orderType == OrderType.LIMIT) {
      if (command.price() == null) {
        throw new BizException(ErrorCode.BAD_REQUEST, "限价单必须提供 price");
      }
      return command.price();
    }
    if (quote.getCurrentPrice() == null) {
      throw new BizException(ErrorCode.MARKET_DATA_UNAVAILABLE);
    }
    return quote.getCurrentPrice();
  }

  private Order buildPendingOrder(
      Long userId,
      PlaceOrderCommand command,
      OrderSide side,
      OrderType orderType,
      BigDecimal price,
      QuoteSnapshot quote) {
    LocalDateTime now = LocalDateTime.now(ZONE_SHANGHAI);
    Order order = new Order();
    order.setUserId(userId);
    order.setClientOrderId(command.clientOrderId());
    order.setStockCode(resolveStockCode(command.stockCode(), quote));
    order.setStockName(resolveStockName(command.stockCode(), quote));
    order.setSide(side);
    order.setOrderType(orderType);
    order.setStatus(OrderStatus.PENDING);
    order.setPrice(price);
    order.setQuantity(command.quantity());
    order.setFilledQuantity(0);
    order.setFilledAmount(BigDecimal.ZERO);
    order.setCommission(BigDecimal.ZERO);
    order.setVersion(0);
    order.setCreatedAt(now);
    order.setUpdatedAt(now);
    return order;
  }

  private String resolveStockCode(String requestedCode, QuoteSnapshot quote) {
    if (quote.getStockCode() != null && !quote.getStockCode().isBlank()) {
      return quote.getStockCode().trim().toLowerCase(Locale.ROOT);
    }
    return requestedCode == null ? "" : requestedCode.trim().toLowerCase(Locale.ROOT);
  }

  private String resolveStockName(String requestedCode, QuoteSnapshot quote) {
    if (quote.getStockName() != null && !quote.getStockName().isBlank()) {
      return quote.getStockName();
    }
    String stockCode = resolveStockCode(requestedCode, quote);
    return stockInfoRepository
        .findByStockCode(stockCode)
        .map(s -> s.getStockName())
        .orElse(stockCode);
  }

  private void freezeSellPosition(Long userId, String stockCode, int quantity) {
    Position position =
        positionRepository
            .findByUserIdAndStockCodeForUpdate(userId, stockCode)
            .orElseThrow(() -> new BizException(ErrorCode.TRADE_ORDER_INSUFFICIENT_POSITION));
    LocalDate today = LocalDate.now(ZONE_SHANGHAI);
    if (position.getFrozenUntil() != null && !today.isAfter(position.getFrozenUntil())) {
      throw new BizException(ErrorCode.TRADE_ORDER_T_PLUS_1);
    }
    if (!hasEnoughAvailable(position, quantity)) {
      throw new BizException(ErrorCode.TRADE_ORDER_INSUFFICIENT_POSITION);
    }

    position.setAvailableQuantity(position.getAvailableQuantity() - quantity);
    position.setFrozenQuantity(position.getFrozenQuantity() + quantity);
    if (!positionRepository.updateWithVersion(position)) {
      throw new BizException(ErrorCode.TRADE_OPTIMISTIC_LOCK_CONFLICT);
    }
  }

  private void settleBuyMatch(Order order, Trade trade) {
    BigDecimal actualCost = trade.getTradeAmount().add(trade.getCommission());
    BigDecimal frozenAmount =
        order.getFrozenAmount() == null ? actualCost : order.getFrozenAmount();
    Account accountAfterSettle =
        accountApplicationService.deductFrozenAndGetAccount(
            order.getUserId(), frozenAmount, actualCost);

    Position position =
        positionRepository
            .findByUserIdAndStockCodeForUpdate(order.getUserId(), order.getStockCode())
            .orElse(null);
    if (position == null) {
      position = new Position();
      position.setUserId(order.getUserId());
      position.setStockCode(order.getStockCode());
      position.setStockName(order.getStockName());
      position.setTotalQuantity(0);
      position.setAvailableQuantity(0);
      position.setFrozenQuantity(0);
      position.setCostPrice(BigDecimal.ZERO);
      position.setTotalCost(BigDecimal.ZERO);
      position.setVersion(0);
      positionDomainService.applyBuyFill(
          position, trade.getTradeQuantity(), trade.getTradePrice(), LocalDate.now(ZONE_SHANGHAI));
      positionRepository.save(position);
    } else {
      positionDomainService.applyBuyFill(
          position, trade.getTradeQuantity(), trade.getTradePrice(), LocalDate.now(ZONE_SHANGHAI));
      position.setStockName(order.getStockName());
      if (!positionRepository.updateWithVersion(position)) {
        throw new BizException(ErrorCode.TRADE_OPTIMISTIC_LOCK_CONFLICT);
      }
    }

    recordFundFlow(
        order.getUserId(),
        FundFlow.FundFlowType.TRADE_BUY,
        actualCost.negate(),
        accountAfterSettle.getAvailableBalance(),
        order.getId(),
        "BUY trade settled");
  }

  private void settleSellMatch(Order order, Trade trade) {
    Position position =
        positionRepository
            .findByUserIdAndStockCodeForUpdate(order.getUserId(), order.getStockCode())
            .orElseThrow(() -> new BizException(ErrorCode.TRADE_ORDER_INSUFFICIENT_POSITION));
    try {
      positionDomainService.applySellFill(position, trade.getTradeQuantity());
    } catch (IllegalStateException ex) {
      throw new BizException(ErrorCode.TRADE_ORDER_INSUFFICIENT_POSITION, ex.getMessage());
    }

    if (position.isCleared()) {
      positionRepository.deleteById(position.getId());
    } else if (!positionRepository.updateWithVersion(position)) {
      throw new BizException(ErrorCode.TRADE_OPTIMISTIC_LOCK_CONFLICT);
    }

    BigDecimal netAmount = trade.getTradeAmount().subtract(trade.getCommission());
    BigDecimal balanceAfter = null;
    if (netAmount.compareTo(BigDecimal.ZERO) > 0) {
      Account accountAfterCredit =
          accountApplicationService.creditBalanceAndGetAccount(order.getUserId(), netAmount);
      balanceAfter = accountAfterCredit.getAvailableBalance();
    }
    recordFundFlow(
        order.getUserId(),
        FundFlow.FundFlowType.TRADE_SELL,
        netAmount,
        balanceAfter,
        order.getId(),
        "SELL trade settled");
  }

  private void unfreezeSellPosition(Long userId, String stockCode, int quantity) {
    Position position =
        positionRepository
            .findByUserIdAndStockCodeForUpdate(userId, stockCode)
            .orElseThrow(() -> new BizException(ErrorCode.TRADE_ORDER_INSUFFICIENT_POSITION));
    int frozen = position.getFrozenQuantity() == null ? 0 : position.getFrozenQuantity();
    int available = position.getAvailableQuantity() == null ? 0 : position.getAvailableQuantity();
    if (frozen < quantity) {
      throw new BizException(ErrorCode.BAD_REQUEST, "冻结持仓不足，无法撤单解冻");
    }

    position.setFrozenQuantity(frozen - quantity);
    position.setAvailableQuantity(available + quantity);
    if (!positionRepository.updateWithVersion(position)) {
      throw new BizException(ErrorCode.TRADE_OPTIMISTIC_LOCK_CONFLICT);
    }
  }

  private void recordFundFlow(
      Long userId,
      FundFlow.FundFlowType type,
      BigDecimal amount,
      BigDecimal balanceAfter,
      Long orderId,
      String remark) {
    FundFlow flow = new FundFlow();
    flow.setUserId(userId);
    flow.setFlowType(type);
    flow.setAmount(amount);
    flow.setBalanceAfter(balanceAfter);
    flow.setOrderId(orderId);
    flow.setRemark(remark);
    flow.setCreatedAt(LocalDateTime.now(ZONE_SHANGHAI));
    fundFlowRepository.save(flow);
  }

  private void publishMatchMessageAfterCommit(Long orderId) {
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              orderMessageProducer.sendMatchMessage(orderId);
            }
          });
      return;
    }
    orderMessageProducer.sendMatchMessage(orderId);
  }

  private void publishTradeFilledEventAfterCommit(Order order, Trade trade) {
    TradeFilledEvent event =
        new TradeFilledEvent(
            order.getId(),
            trade.getId(),
            order.getUserId(),
            order.getStockCode(),
            order.getStockName(),
            order.getSide().name(),
            trade.getTradePrice(),
            trade.getTradeQuantity(),
            trade.getTradeAmount(),
            trade.getCommission(),
            trade.getTradedAt());
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              orderMessageProducer.sendTradeFilledEvent(event);
            }
          });
      return;
    }
    orderMessageProducer.sendTradeFilledEvent(event);
  }

  private void logTransactionCost(String action, Long userId, Long orderId, long startNano) {
    long elapsedMs = (System.nanoTime() - startNano) / 1_000_000;
    if (elapsedMs > TX_TARGET_MS) {
      log.warn(
          "trade.{}.slow userId={} orderId={} elapsedMs={} targetMs={}",
          action,
          userId,
          orderId,
          elapsedMs,
          TX_TARGET_MS);
      return;
    }
    log.debug("trade.{}.ok userId={} orderId={} elapsedMs={}", action, userId, orderId, elapsedMs);
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

  private OrderSide parseOrderSide(String side) {
    try {
      return OrderSide.valueOf(side.trim().toUpperCase(Locale.ROOT));
    } catch (Exception ex) {
      throw new BizException(ErrorCode.BAD_REQUEST, "不支持的交易方向: " + side);
    }
  }

  private OrderType parseOrderType(String orderType) {
    try {
      return OrderType.valueOf(orderType.trim().toUpperCase(Locale.ROOT));
    } catch (Exception ex) {
      throw new BizException(ErrorCode.BAD_REQUEST, "不支持的订单类型: " + orderType);
    }
  }

  private boolean hasEnoughAvailable(Position position, int quantity) {
    Integer available = position.getAvailableQuantity();
    return available != null && available >= quantity;
  }

  private boolean hasPositiveAmount(BigDecimal amount) {
    return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
  }

  private int sanitizePage(int page) {
    return page < MIN_PAGE ? MIN_PAGE : page;
  }

  private int sanitizeSize(int size) {
    if (size <= 0) {
      return DEFAULT_SIZE;
    }
    return Math.min(size, MAX_PAGE_SIZE);
  }

  private TradingWindow loadTradingWindow() {
    Cache cache = cacheManager.getCache(CaffeineConfig.CACHE_CONFIG);
    if (cache == null) {
      return buildWindowFromConfig();
    }
    TradingWindow cached = cache.get(TRADE_WINDOW_CACHE_KEY, TradingWindow.class);
    if (cached != null) {
      return cached;
    }
    TradingWindow window = buildWindowFromConfig();
    cache.put(TRADE_WINDOW_CACHE_KEY, window);
    return window;
  }

  private TradingWindow buildWindowFromConfig() {
    return new TradingWindow(
        tradeRuleConfig.getMorningStart(),
        tradeRuleConfig.getMorningEnd(),
        tradeRuleConfig.getAfternoonStart(),
        tradeRuleConfig.getAfternoonEnd());
  }

  private OrderVO toOrderVO(Order order) {
    return new OrderVO(
        order.getId(),
        order.getClientOrderId(),
        order.getStockCode(),
        order.getStockName(),
        order.getSide().name(),
        order.getOrderType().name(),
        order.getStatus().name(),
        order.getPrice(),
        order.getQuantity(),
        order.getFilledQuantity(),
        order.getFilledAmount(),
        order.getCommission(),
        order.getCreatedAt(),
        order.getUpdatedAt());
  }

  private TradeVO toTradeVO(Trade trade) {
    return new TradeVO(
        trade.getId(),
        trade.getOrderId(),
        trade.getStockCode(),
        trade.getStockName(),
        trade.getSide().name(),
        trade.getTradePrice(),
        trade.getTradeQuantity(),
        trade.getTradeAmount(),
        trade.getCommission(),
        trade.getTradedAt());
  }

  private record TradingWindow(
      LocalTime morningOpen,
      LocalTime morningClose,
      LocalTime afternoonOpen,
      LocalTime afternoonClose) {}
}
