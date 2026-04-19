package com.lzbsdsg.stocksimulation.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lzbsdsg.stocksimulation.auth.infrastructure.gateway.JwtTokenProvider;
import com.lzbsdsg.stocksimulation.watchlist.application.WatchlistApplicationService;
import com.lzbsdsg.stocksimulation.watchlist.controller.WatchlistController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(WatchlistController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(
    properties = {
      "DEV_BASIC_AUTH_PASSWORD=test",
      "JWT_SECRET=test-secret-key-for-handler-module",
      "DB_PASSWORD=test",
      "RABBITMQ_PASSWORD=test"
    })
class GlobalExceptionHandlerApiTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private WatchlistApplicationService watchlistApplicationService;
  @MockBean private JwtTokenProvider jwtTokenProvider;

  @Test
  void should_return_405_when_method_not_supported() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/watchlist")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"stockCode\":\"sh600519\"}"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.code").value(400))
        .andExpect(jsonPath("$.message").value("请求方法不支持"));
  }
}
