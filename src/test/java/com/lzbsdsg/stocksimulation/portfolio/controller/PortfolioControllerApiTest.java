package com.lzbsdsg.stocksimulation.portfolio.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lzbsdsg.stocksimulation.auth.infrastructure.gateway.JwtTokenProvider;
import com.lzbsdsg.stocksimulation.common.result.PageResult;
import com.lzbsdsg.stocksimulation.portfolio.application.PortfolioApplicationService;
import com.lzbsdsg.stocksimulation.portfolio.application.vo.EquityCurvePointVO;
import com.lzbsdsg.stocksimulation.portfolio.application.vo.EquityCurveVO;
import com.lzbsdsg.stocksimulation.portfolio.application.vo.FundFlowVO;
import com.lzbsdsg.stocksimulation.portfolio.application.vo.OverviewVO;
import com.lzbsdsg.stocksimulation.portfolio.application.vo.PositionVO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PortfolioController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(
    properties = {
      "DEV_BASIC_AUTH_PASSWORD=test",
      "JWT_SECRET=test-secret-key-for-portfolio-module",
      "DB_PASSWORD=test",
      "RABBITMQ_PASSWORD=test"
    })
class PortfolioControllerApiTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private PortfolioApplicationService portfolioApplicationService;
  @MockBean private JwtTokenProvider jwtTokenProvider;

  @Test
  void should_return_overview_200() throws Exception {
    when(portfolioApplicationService.getOverview())
        .thenReturn(
            new OverviewVO(
                new BigDecimal("523456.78"),
                new BigDecimal("354456.78"),
                BigDecimal.ZERO,
                new BigDecimal("169000.00"),
                new BigDecimal("500000.00"),
                new BigDecimal("23456.78"),
                new BigDecimal("4.6914"),
                new BigDecimal("1250.00"),
                new BigDecimal("0.2394")));

    mockMvc
        .perform(get("/api/v1/portfolio/overview"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(200))
        .andExpect(jsonPath("$.data.totalAssets").value(523456.78))
        .andExpect(jsonPath("$.data.marketValue").value(169000.0));
  }

  @Test
  void should_return_positions_with_realtime_profit() throws Exception {
    when(portfolioApplicationService.getPositions())
        .thenReturn(
            List.of(
                new PositionVO(
                    1L,
                    "sh600519",
                    "贵州茅台",
                    100,
                    100,
                    0,
                    new BigDecimal("1688.0000"),
                    new BigDecimal("1690.0000"),
                    new BigDecimal("169000.00"),
                    new BigDecimal("200.00"),
                    new BigDecimal("0.1185"),
                    new BigDecimal("120.00"),
                    null)));

    mockMvc
        .perform(get("/api/v1/portfolio/positions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(200))
        .andExpect(jsonPath("$.data[0].stockCode").value("sh600519"))
        .andExpect(jsonPath("$.data[0].profit").value(200.0));
  }

  @Test
  void should_return_fund_flows_with_pagination() throws Exception {
    PageResult<FundFlowVO> pageResult =
        new PageResult<>(
            List.of(
                new FundFlowVO(
                    11L,
                    "TRADE_BUY",
                    new BigDecimal("-168800.00"),
                    new BigDecimal("331200.00"),
                    20001L,
                    "买入 贵州茅台",
                    LocalDateTime.of(2026, 3, 25, 10, 15, 35))),
            1,
            1,
            20);
    when(portfolioApplicationService.getFundFlows(1, 20)).thenReturn(pageResult);

    mockMvc
        .perform(get("/api/v1/portfolio/fund-flows").param("page", "1").param("size", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(200))
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.records[0].flowType").value("TRADE_BUY"));
  }

  @Test
  void should_return_equity_curve() throws Exception {
    when(portfolioApplicationService.getEquityCurve(30))
        .thenReturn(
            new EquityCurveVO(
                List.of(
                    new EquityCurvePointVO(
                        LocalDate.of(2026, 3, 24),
                        new BigDecimal("500000.00"),
                        BigDecimal.ZERO),
                    new EquityCurvePointVO(
                        LocalDate.of(2026, 3, 25),
                        new BigDecimal("501200.00"),
                        new BigDecimal("0.2400"))),
                new BigDecimal("1.8000"),
                LocalDate.of(2026, 3, 25)));

    mockMvc
        .perform(get("/api/v1/portfolio/equity-curve").param("days", "30"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(200))
        .andExpect(jsonPath("$.data.points[1].totalAssets").value(501200.0))
        .andExpect(jsonPath("$.data.maxDrawdown").value(1.8));
  }
}

