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
import com.lzbsdsg.stocksimulation.user.application.AccountApplicationService;
import com.lzbsdsg.stocksimulation.user.domain.entity.Account;
import com.lzbsdsg.stocksimulation.user.domain.repository.AccountRepository;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** 交易应用服务单元测试。 */
@ExtendWith(MockitoExtension.class)
class TradeApplicationServiceTest {

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
}
