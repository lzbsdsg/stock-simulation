package com.lzbsdsg.stocksimulation.trade.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lzbsdsg.stocksimulation.auth.infrastructure.gateway.JwtTokenProvider;
import com.lzbsdsg.stocksimulation.common.result.PageResult;
import com.lzbsdsg.stocksimulation.trade.application.TradeApplicationService;
import com.lzbsdsg.stocksimulation.trade.application.command.PlaceOrderCommand;
import com.lzbsdsg.stocksimulation.trade.application.vo.OrderVO;
import com.lzbsdsg.stocksimulation.trade.application.vo.TradeVO;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** 订单控制器 API 测试。 */
@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(
    properties = {
      "DEV_BASIC_AUTH_PASSWORD=test",
      "JWT_SECRET=test-secret-key-for-trade-module",
      "DB_PASSWORD=test",
      "RABBITMQ_PASSWORD=test"
    })
class OrderControllerApiTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockBean private TradeApplicationService tradeApplicationService;
  @MockBean private JwtTokenProvider jwtTokenProvider;

  @Test
  void should_place_order_success() throws Exception {
    OrderVO orderVO =
        new OrderVO(
            10001L,
            "cid-10001",
            "sh600519",
            "贵州茅台",
            "BUY",
            "LIMIT",
            "PENDING",
            new BigDecimal("1688.88"),
            100,
            0,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            LocalDateTime.now(),
            LocalDateTime.now());
    when(tradeApplicationService.placeOrder(any(PlaceOrderCommand.class))).thenReturn(orderVO);

    mockMvc
        .perform(
            post("/api/v1/trade/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new PlaceOrderCommand(
                            "cid-10001",
                            "sh600519",
                            "BUY",
                            "LIMIT",
                            new BigDecimal("1688.88"),
                            100))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(200))
        .andExpect(jsonPath("$.data.orderId").value(10001))
        .andExpect(jsonPath("$.data.status").value("PENDING"));
  }

  @Test
  void should_reject_place_order_when_quantity_invalid() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/trade/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new PlaceOrderCommand(
                            "cid-invalid",
                            "sh600519",
                            "BUY",
                            "LIMIT",
                            new BigDecimal("1688.88"),
                            10))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void should_cancel_order_success() throws Exception {
    doNothing().when(tradeApplicationService).cancelOrder(10001L);

    mockMvc
        .perform(delete("/api/v1/trade/orders/10001"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(200));
  }

  @Test
  void should_get_orders_with_scope_and_pagination() throws Exception {
    PageResult<OrderVO> pageResult =
        new PageResult<>(
            List.of(
                new OrderVO(
                    10001L,
                    "cid-10001",
                    "sh600519",
                    "贵州茅台",
                    "BUY",
                    "LIMIT",
                    "PENDING",
                    new BigDecimal("1688.88"),
                    100,
                    0,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    LocalDateTime.now(),
                    LocalDateTime.now())),
            1L,
            1,
            20);
    when(tradeApplicationService.getOrders("today", 1, 20)).thenReturn(pageResult);

    mockMvc
        .perform(get("/api/v1/trade/orders").param("scope", "today").param("page", "1").param("size", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.records[0].stockCode").value("sh600519"));
  }

  @Test
  void should_get_trades_with_pagination() throws Exception {
    PageResult<TradeVO> pageResult =
        new PageResult<>(
            List.of(
                new TradeVO(
                    30001L,
                    10001L,
                    "sh600519",
                    "贵州茅台",
                    "BUY",
                    new BigDecimal("1688.00"),
                    100,
                    new BigDecimal("168800.00"),
                    new BigDecimal("54.02"),
                    LocalDateTime.now())),
            1L,
            1,
            20);
    when(tradeApplicationService.getTrades(1, 20)).thenReturn(pageResult);

    mockMvc
        .perform(get("/api/v1/trade/trades").param("page", "1").param("size", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.records[0].tradeId").value(30001));
  }
}
