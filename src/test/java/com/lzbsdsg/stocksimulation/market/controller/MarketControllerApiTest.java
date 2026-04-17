package com.lzbsdsg.stocksimulation.market.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lzbsdsg.stocksimulation.auth.infrastructure.gateway.JwtTokenProvider;
import com.lzbsdsg.stocksimulation.market.application.MarketApplicationService;
import com.lzbsdsg.stocksimulation.market.application.vo.KLineVO;
import com.lzbsdsg.stocksimulation.market.application.vo.MarketLatencyMetricVO;
import com.lzbsdsg.stocksimulation.market.application.vo.MarketIndexQuoteVO;
import com.lzbsdsg.stocksimulation.market.application.vo.MarketRealtimeMetricsVO;
import com.lzbsdsg.stocksimulation.market.application.vo.QuoteVO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MarketController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
        "DEV_BASIC_AUTH_PASSWORD=test",
        "JWT_SECRET=test-secret-key-for-market-module",
        "DB_PASSWORD=test",
        "RABBITMQ_PASSWORD=test"
})
class MarketControllerApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MarketApplicationService marketApplicationService;
    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void should_get_single_quote() throws Exception {
        when(marketApplicationService.getQuote("sh600519"))
                .thenReturn(
                        new QuoteVO(
                                "sh600519",
                                "贵州茅台",
                                new BigDecimal("1688.88"),
                                new BigDecimal("1660.00"),
                                new BigDecimal("1650.00"),
                                new BigDecimal("1690.00"),
                                new BigDecimal("1640.00"),
                                10000L,
                                new BigDecimal("16888800.00"),
                                new BigDecimal("2.36"),
                                LocalDateTime.now()));

        mockMvc
                .perform(get("/api/v1/market/quote/sh600519"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.stockCode").value("sh600519"))
                .andExpect(jsonPath("$.data.stockName").value("贵州茅台"));
    }

    @Test
    void should_get_batch_quotes_by_codes_param() throws Exception {
        when(marketApplicationService.batchGetQuotes(any()))
                .thenReturn(
                        List.of(
                                new QuoteVO(
                                        "sh600519",
                                        "贵州茅台",
                                        new BigDecimal("1688.88"),
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        LocalDateTime.now())));

        mockMvc
                .perform(get("/api/v1/market/quotes").param("codes", "sh600519", "sz000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].stockCode").value("sh600519"));
    }

    @Test
    void should_get_kline() throws Exception {
        when(marketApplicationService.getKLine(
                "sh600519", "DAILY", LocalDate.parse("2026-03-01"), LocalDate.parse("2026-03-05")))
                .thenReturn(
                        List.of(
                                new KLineVO(
                                        LocalDate.parse("2026-03-01"),
                                        new BigDecimal("10.00"),
                                        new BigDecimal("10.20"),
                                        new BigDecimal("10.30"),
                                        new BigDecimal("9.80"),
                                        10000L,
                                        new BigDecimal("100000.00"))));

        mockMvc
                .perform(
                        get("/api/v1/market/kline/sh600519")
                                .param("period", "DAILY")
                                .param("from", "2026-03-01")
                                .param("to", "2026-03-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].date").value("2026-03-01"));
    }

    @Test
    void should_get_official_indexes() throws Exception {
        when(marketApplicationService.getOfficialIndexQuotes())
                .thenReturn(
                        List.of(
                                new MarketIndexQuoteVO(
                                        "sh000001",
                                        "上证指数",
                                        new BigDecimal("3210.15"),
                                        new BigDecimal("12.30"),
                                        new BigDecimal("0.38"),
                                        123456789L,
                                        new BigDecimal("1234567890.00"))));

        mockMvc
                .perform(get("/api/v1/market/indexes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].stockCode").value("sh000001"));
    }

    @Test
    void should_report_visible_codes() throws Exception {
        mockMvc
                .perform(
                        post("/api/v1/market/visible-codes")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("[\"sh600519\",\"sz000001\"]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(marketApplicationService).reportVisibleCodes(any());
    }

    @Test
    void should_get_realtime_metrics() throws Exception {
        when(marketApplicationService.getRealtimeMetrics())
                .thenReturn(
                        new MarketRealtimeMetricsVO(
                                LocalDateTime.now(),
                                12L,
                                40,
                                38,
                                15L,
                                120L,
                                4L,
                                false,
                                2.0d,
                                new MarketLatencyMetricVO("market.ingest.cycle.duration", 10L, 8.2d, 15.6d, 12.0d,
                                        14.5d),
                                new MarketLatencyMetricVO("market.pubsub.fanout.delay", 10L, 3.1d, 7.0d, 5.6d, 6.5d),
                                new MarketLatencyMetricVO("market.ws.queue.delay", 50L, 6.4d, 20.0d, 12.3d, 18.2d),
                                new MarketLatencyMetricVO("ws_push_duration_seconds", 50L, 1.1d, 5.4d, 3.2d, 4.8d)));

        mockMvc
                .perform(get("/api/v1/market/realtime-metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.activeCodeCount").value(12))
                .andExpect(jsonPath("$.data.lastIngestCodeCount").value(40))
                .andExpect(jsonPath("$.data.ingestCycleLatency.metric").value("market.ingest.cycle.duration"));
    }
}
