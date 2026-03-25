package com.lzbsdsg.stocksimulation.trade.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lzbsdsg.stocksimulation.common.exception.BizException;
import com.lzbsdsg.stocksimulation.common.result.ErrorCode;
import com.lzbsdsg.stocksimulation.config.TradeRuleConfig;
import com.lzbsdsg.stocksimulation.market.domain.entity.QuoteSnapshot;
import com.lzbsdsg.stocksimulation.market.domain.repository.StockInfoRepository;
import com.lzbsdsg.stocksimulation.market.domain.service.MarketDataFacade;
import com.lzbsdsg.stocksimulation.portfolio.domain.entity.Position;
import com.lzbsdsg.stocksimulation.portfolio.domain.repository.FundFlowRepository;
import com.lzbsdsg.stocksimulation.portfolio.domain.repository.PositionRepository;
import com.lzbsdsg.stocksimulation.trade.application.command.CancelOrderCommand;
import com.lzbsdsg.stocksimulation.trade.application.command.PlaceOrderCommand;
import com.lzbsdsg.stocksimulation.trade.application.vo.OrderVO;
import com.lzbsdsg.stocksimulation.trade.domain.entity.Order;
import com.lzbsdsg.stocksimulation.trade.domain.entity.OrderSide;
import com.lzbsdsg.stocksimulation.trade.domain.entity.OrderStatus;
import com.lzbsdsg.stocksimulation.trade.domain.entity.OrderType;
import com.lzbsdsg.stocksimulation.trade.domain.repository.OrderRepository;
import com.lzbsdsg.stocksimulation.trade.domain.repository.TradeRepository;
import com.lzbsdsg.stocksimulation.trade.infrastructure.gateway.IdempotencyGateway;
import com.lzbsdsg.stocksimulation.trade.infrastructure.mq.OrderMessageProducer;
import com.lzbsdsg.stocksimulation.trade.infrastructure.mq.TradeFilledEvent;
import com.lzbsdsg.stocksimulation.user.application.AccountApplicationService;
import com.lzbsdsg.stocksimulation.user.domain.entity.Account;
import com.lzbsdsg.stocksimulation.user.domain.repository.AccountRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** 交易应用服务单元测试。 */
@ExtendWith(MockitoExtension.class)
class TradeApplicationServiceTest {

  private static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");

  @Mock private OrderRepository orderRepository;
  @Mock private TradeRepository tradeRepository;
  @Mock private IdempotencyGateway idempotencyGateway;
  @Mock private OrderMessageProducer orderMessageProducer;
  @Mock private MarketDataFacade marketDataFacade;
  @Mock private StockInfoRepository stockInfoRepository;
  @Mock private AccountApplicationService accountApplicationService;
  @Mock private AccountRepository accountRepository;
  @Mock private FundFlowRepository fundFlowRepository;
  @Mock private PositionRepository positionRepository;

  private TradeApplicationService tradeApplicationService;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken("1001", null));
    TradeRuleConfig tradeRuleConfig = new TradeRuleConfig();
    tradeRuleConfig.setMorningStart(LocalTime.MIN);
    tradeRuleConfig.setMorningEnd(LocalTime.MAX);
    tradeRuleConfig.setAfternoonStart(LocalTime.MIN);
    tradeRuleConfig.setAfternoonEnd(LocalTime.MAX);
    tradeApplicationService =
        new TradeApplicationService(
            orderRepository,
            tradeRepository,
            idempotencyGateway,
            orderMessageProducer,
            marketDataFacade,
            stockInfoRepository,
            accountApplicationService,
            accountRepository,
            fundFlowRepository,
            positionRepository,
            new ConcurrentMapCacheManager(),
            tradeRuleConfig);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void should_place_buy_order_and_send_match_message() {
    when(idempotencyGateway.tryAcquire("cid-buy-1")).thenReturn(true);
    when(marketDataFacade.getQuote("sh600519")).thenReturn(mockQuote("sh600519", "贵州茅台"));
    when(accountRepository.findByUserId(1001L)).thenReturn(Optional.of(account("8999.68")));
    doAnswer(
            invocation -> {
              Order order = invocation.getArgument(0);
              order.setId(9001L);
              return null;
            })
        .when(orderRepository)
        .save(any(Order.class));

    PlaceOrderCommand command =
        new PlaceOrderCommand("cid-buy-1", "sh600519", "BUY", "LIMIT", new BigDecimal("10.00"), 100);
    OrderVO result = tradeApplicationService.placeOrder(command);

    assertNotNull(result);
    assertEquals(9001L, result.orderId());
    assertEquals("PENDING", result.status());
    verify(accountApplicationService).freezeBalance(eq(1001L), eq(new BigDecimal("1000.32")));
    verify(fundFlowRepository).save(any());
    verify(orderMessageProducer).sendMatchMessage(9001L);
    verify(idempotencyGateway, never()).release("cid-buy-1");
  }

  @Test
  void should_place_sell_order_and_freeze_position() {
    when(idempotencyGateway.tryAcquire("cid-sell-1")).thenReturn(true);
    when(marketDataFacade.getQuote("sh600519")).thenReturn(mockQuote("sh600519", "贵州茅台"));
    when(positionRepository.findByUserIdAndStockCodeForUpdate(1001L, "sh600519"))
        .thenReturn(Optional.of(position(200, 0)));
    when(positionRepository.updateWithVersion(any(Position.class))).thenReturn(true);
    doAnswer(
            invocation -> {
              Order order = invocation.getArgument(0);
              order.setId(9002L);
              return null;
            })
        .when(orderRepository)
        .save(any(Order.class));

    PlaceOrderCommand command =
        new PlaceOrderCommand("cid-sell-1", "sh600519", "SELL", "LIMIT", new BigDecimal("10.00"), 100);
    OrderVO result = tradeApplicationService.placeOrder(command);

    assertEquals(9002L, result.orderId());
    assertEquals("SELL", result.side());
    verify(accountApplicationService, never()).freezeBalance(any(), any());
    verify(positionRepository).updateWithVersion(any(Position.class));
    verify(orderMessageProducer).sendMatchMessage(9002L);
    verify(idempotencyGateway, never()).release("cid-sell-1");
  }

  @Test
  void should_reject_duplicate_client_order_id() {
    when(idempotencyGateway.tryAcquire("cid-dup")).thenReturn(false);
    PlaceOrderCommand command =
        new PlaceOrderCommand("cid-dup", "sh600519", "BUY", "LIMIT", new BigDecimal("10.00"), 100);

    BizException ex = assertThrows(BizException.class, () -> tradeApplicationService.placeOrder(command));

    assertEquals(ErrorCode.TRADE_ORDER_DUPLICATE, ex.getErrorCode());
    verify(orderRepository, never()).save(any(Order.class));
    verify(orderMessageProducer, never()).sendMatchMessage(any());
    verify(idempotencyGateway, never()).release("cid-dup");
  }

  @Test
  void should_reject_buy_order_when_insufficient_fund() {
    when(idempotencyGateway.tryAcquire("cid-fund")).thenReturn(true);
    when(marketDataFacade.getQuote("sh600519")).thenReturn(mockQuote("sh600519", "贵州茅台"));
    doAnswer(
            invocation -> {
              throw new BizException(ErrorCode.TRADE_ORDER_INSUFFICIENT_FUND);
            })
        .when(accountApplicationService)
        .freezeBalance(any(), any());

    PlaceOrderCommand command =
        new PlaceOrderCommand("cid-fund", "sh600519", "BUY", "LIMIT", new BigDecimal("10.00"), 100);

    BizException ex = assertThrows(BizException.class, () -> tradeApplicationService.placeOrder(command));

    assertEquals(ErrorCode.TRADE_ORDER_INSUFFICIENT_FUND, ex.getErrorCode());
    verify(orderRepository, never()).save(any(Order.class));
    verify(orderMessageProducer, never()).sendMatchMessage(any());
    verify(idempotencyGateway).release("cid-fund");
  }

  @Test
  void should_cancel_pending_buy_order_and_unfreeze_balance() {
    Order order = new Order();
    order.setId(9100L);
    order.setUserId(1001L);
    order.setSide(OrderSide.BUY);
    order.setOrderType(OrderType.LIMIT);
    order.setStatus(OrderStatus.PENDING);
    order.setStockCode("sh600519");
    order.setQuantity(100);
    order.setFilledQuantity(0);
    order.setFrozenAmount(new BigDecimal("1000.32"));
    when(orderRepository.findById(9100L)).thenReturn(Optional.of(order));
    when(orderRepository.updateWithVersion(any(Order.class))).thenReturn(true);
    when(accountRepository.findByUserId(1001L)).thenReturn(Optional.of(account("10000.00")));

    tradeApplicationService.cancelOrder(new CancelOrderCommand(9100L));

    verify(accountApplicationService).unfreezeBalance(1001L, new BigDecimal("1000.32"));
    verify(fundFlowRepository).save(any());
  }

  @Test
  void should_reject_cancel_when_order_not_cancellable() {
    Order order = new Order();
    order.setId(9101L);
    order.setUserId(1001L);
    order.setStatus(OrderStatus.FILLED);
    when(orderRepository.findById(9101L)).thenReturn(Optional.of(order));

    BizException ex =
        assertThrows(
            BizException.class, () -> tradeApplicationService.cancelOrder(new CancelOrderCommand(9101L)));

    assertEquals(ErrorCode.TRADE_ORDER_CANNOT_CANCEL, ex.getErrorCode());
    verify(orderRepository, never()).updateWithVersion(any(Order.class));
  }

  @Test
  void should_reject_cancel_when_order_snapshot_invalid() {
    Order order = new Order();
    order.setId(9102L);
    order.setUserId(1001L);
    order.setStatus(OrderStatus.PENDING);
    order.setQuantity(100);
    order.setFilledQuantity(0);
    when(orderRepository.findById(9102L)).thenReturn(Optional.of(order));

    BizException ex =
        assertThrows(
            BizException.class, () -> tradeApplicationService.cancelOrder(new CancelOrderCommand(9102L)));

    assertEquals(ErrorCode.TRADE_ORDER_CANNOT_CANCEL, ex.getErrorCode());
    verify(orderRepository, never()).updateWithVersion(any(Order.class));
  }

  @Test
  void should_convert_unexpected_cancel_error_to_optimistic_lock_conflict() {
    Order order = new Order();
    order.setId(9103L);
    order.setUserId(1001L);
    order.setSide(OrderSide.BUY);
    order.setOrderType(OrderType.LIMIT);
    order.setStatus(OrderStatus.PENDING);
    order.setStockCode("sh600519");
    order.setQuantity(100);
    order.setFilledQuantity(0);
    order.setFrozenAmount(new BigDecimal("1000.32"));
    when(orderRepository.findById(9103L)).thenReturn(Optional.of(order));
    when(orderRepository.updateWithVersion(any(Order.class))).thenReturn(true);
    doAnswer(
            invocation -> {
              throw new RuntimeException("unexpected");
            })
        .when(accountApplicationService)
        .unfreezeBalance(1001L, new BigDecimal("1000.32"));

    BizException ex =
        assertThrows(
            BizException.class, () -> tradeApplicationService.cancelOrder(new CancelOrderCommand(9103L)));

    assertEquals(ErrorCode.TRADE_OPTIMISTIC_LOCK_CONFLICT, ex.getErrorCode());
  }

  @Test
  void should_match_buy_order_and_settle_account_position() {
    Order order = pendingOrder(9201L, OrderSide.BUY, new BigDecimal("10.00"), 100, new BigDecimal("1005.00"));
    when(orderRepository.findById(9201L)).thenReturn(Optional.of(order));
    when(orderRepository.updateWithVersion(any(Order.class))).thenReturn(true);
    when(marketDataFacade.getQuote("sh600519")).thenReturn(mockQuote("sh600519", "贵州茅台"));
    when(positionRepository.findByUserIdAndStockCodeForUpdate(1001L, "sh600519"))
        .thenReturn(Optional.empty());
    when(accountRepository.findByUserId(1001L)).thenReturn(Optional.of(account("8995.00")));
    doAnswer(
            invocation -> {
              com.lzbsdsg.stocksimulation.trade.domain.entity.Trade trade = invocation.getArgument(0);
              trade.setId(7001L);
              return null;
            })
        .when(tradeRepository)
        .save(any());

    TradeApplicationService.MatchResult result = tradeApplicationService.matchOrder(9201L);

    assertEquals(TradeApplicationService.MatchResult.MATCHED, result);
    verify(accountApplicationService).deductFrozen(1001L, new BigDecimal("1005.00"), new BigDecimal("1005.02"));
    verify(positionRepository).save(any(Position.class));
    verify(fundFlowRepository).save(any());
    verify(orderMessageProducer).sendTradeFilledEvent(any(TradeFilledEvent.class));
  }

  @Test
  void should_match_sell_order_and_credit_balance() {
    Order order = pendingOrder(9202L, OrderSide.SELL, new BigDecimal("10.00"), 100, BigDecimal.ZERO);
    Position position = position(100, 100);
    position.setId(6001L);
    position.setTotalQuantity(200);
    position.setCostPrice(new BigDecimal("8.00"));
    position.setTotalCost(new BigDecimal("1600.00"));
    when(orderRepository.findById(9202L)).thenReturn(Optional.of(order));
    when(orderRepository.updateWithVersion(any(Order.class))).thenReturn(true);
    when(marketDataFacade.getQuote("sh600519")).thenReturn(mockQuote("sh600519", "贵州茅台"));
    when(positionRepository.findByUserIdAndStockCodeForUpdate(1001L, "sh600519"))
        .thenReturn(Optional.of(position));
    when(positionRepository.updateWithVersion(any(Position.class))).thenReturn(true);
    when(accountRepository.findByUserId(1001L)).thenReturn(Optional.of(account("10000.00")));
    doAnswer(
            invocation -> {
              com.lzbsdsg.stocksimulation.trade.domain.entity.Trade trade = invocation.getArgument(0);
              trade.setId(7002L);
              return null;
            })
        .when(tradeRepository)
        .save(any());

    TradeApplicationService.MatchResult result = tradeApplicationService.matchOrder(9202L);

    assertEquals(TradeApplicationService.MatchResult.MATCHED, result);
    verify(accountApplicationService).creditBalance(1001L, new BigDecimal("993.98"));
    verify(positionRepository).updateWithVersion(any(Position.class));
    verify(fundFlowRepository).save(any());
  }

  @Test
  void should_skip_match_when_price_not_reached() {
    Order order = pendingOrder(9203L, OrderSide.BUY, new BigDecimal("10.00"), 100, new BigDecimal("1005.00"));
    when(orderRepository.findById(9203L)).thenReturn(Optional.of(order));
    QuoteSnapshot quote = mockQuote("sh600519", "贵州茅台");
    quote.setCurrentPrice(new BigDecimal("10.10"));
    when(marketDataFacade.getQuote("sh600519")).thenReturn(quote);

    TradeApplicationService.MatchResult result = tradeApplicationService.matchOrder(9203L);

    assertEquals(TradeApplicationService.MatchResult.SKIPPED_PRICE_NOT_MATCHED, result);
    verify(tradeRepository, never()).save(any());
    verify(orderRepository, never()).updateWithVersion(any(Order.class));
  }

  @Test
  void should_throw_when_match_order_update_conflict() {
    Order order = pendingOrder(9204L, OrderSide.BUY, new BigDecimal("10.00"), 100, new BigDecimal("1005.00"));
    when(orderRepository.findById(9204L)).thenReturn(Optional.of(order));
    when(orderRepository.updateWithVersion(any(Order.class))).thenReturn(false);
    when(marketDataFacade.getQuote("sh600519")).thenReturn(mockQuote("sh600519", "贵州茅台"));

    BizException ex = assertThrows(BizException.class, () -> tradeApplicationService.matchOrder(9204L));

    assertEquals(ErrorCode.TRADE_OPTIMISTIC_LOCK_CONFLICT, ex.getErrorCode());
    verify(tradeRepository, never()).save(any());
  }

  @Test
  void should_archive_closed_orders_with_given_retention_and_batch_size() {
    when(orderRepository.archiveClosedOrdersWithoutTrades(any(LocalDateTime.class), eq(300)))
        .thenReturn(12);

    int archived = tradeApplicationService.archiveClosedOrders(14, 300);

    assertEquals(12, archived);
    ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
    verify(orderRepository).archiveClosedOrdersWithoutTrades(cutoffCaptor.capture(), eq(300));
    LocalDateTime cutoff = cutoffCaptor.getValue();
    assertEquals(LocalDate.now(ZONE_SHANGHAI).minusDays(14), cutoff.toLocalDate());
    assertEquals(LocalTime.MIN, cutoff.toLocalTime());
  }

  @Test
  void should_fallback_to_default_archive_parameters_when_input_invalid() {
    when(orderRepository.archiveClosedOrdersWithoutTrades(any(LocalDateTime.class), eq(500)))
        .thenReturn(3);

    int archived = tradeApplicationService.archiveClosedOrders(0, 0);

    assertEquals(3, archived);
    ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
    verify(orderRepository).archiveClosedOrdersWithoutTrades(cutoffCaptor.capture(), eq(500));
    LocalDateTime cutoff = cutoffCaptor.getValue();
    assertEquals(LocalDate.now(ZONE_SHANGHAI).minusDays(1), cutoff.toLocalDate());
    assertEquals(LocalTime.MIN, cutoff.toLocalTime());
  }

  private QuoteSnapshot mockQuote(String code, String name) {
    QuoteSnapshot quote = new QuoteSnapshot();
    quote.setStockCode(code);
    quote.setStockName(name);
    quote.setCurrentPrice(new BigDecimal("10.00"));
    quote.setLowerLimitPrice(new BigDecimal("9.00"));
    quote.setUpperLimitPrice(new BigDecimal("11.00"));
    return quote;
  }

  private Account account(String availableBalance) {
    Account account = new Account();
    account.setUserId(1001L);
    account.setAvailableBalance(new BigDecimal(availableBalance));
    account.setFrozenBalance(BigDecimal.ZERO);
    account.setInitialBalance(new BigDecimal("100000.00"));
    return account;
  }

  private Position position(int availableQuantity, int frozenQuantity) {
    Position position = new Position();
    position.setUserId(1001L);
    position.setStockCode("sh600519");
    position.setAvailableQuantity(availableQuantity);
    position.setFrozenQuantity(frozenQuantity);
    position.setVersion(0);
    return position;
  }

  private Order pendingOrder(
      Long orderId, OrderSide side, BigDecimal price, int quantity, BigDecimal frozenAmount) {
    Order order = new Order();
    order.setId(orderId);
    order.setUserId(1001L);
    order.setClientOrderId("match-" + orderId);
    order.setStockCode("sh600519");
    order.setStockName("贵州茅台");
    order.setSide(side);
    order.setOrderType(OrderType.LIMIT);
    order.setStatus(OrderStatus.PENDING);
    order.setPrice(price);
    order.setQuantity(quantity);
    order.setFilledQuantity(0);
    order.setFilledAmount(BigDecimal.ZERO);
    order.setCommission(BigDecimal.ZERO);
    order.setFrozenAmount(frozenAmount);
    order.setVersion(0);
    return order;
  }
}
