package com.lzbsdsg.stocksimulation.admin.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lzbsdsg.stocksimulation.admin.application.AdminApplicationService;
import com.lzbsdsg.stocksimulation.auth.infrastructure.gateway.JwtTokenProvider;
import com.lzbsdsg.stocksimulation.config.JwtAuthenticationFilter;
import com.lzbsdsg.stocksimulation.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
@TestPropertySource(
    properties = {
      "DEV_BASIC_AUTH_PASSWORD=test",
      "JWT_SECRET=test-secret-key-for-auth-module",
      "DB_PASSWORD=test",
      "RABBITMQ_PASSWORD=test"
    })
class AdminControllerSecurityTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private AdminApplicationService adminApplicationService;
  @MockBean private JwtTokenProvider jwtTokenProvider;

  @Test
  void should_return_403_when_missing_token() throws Exception {
    mockMvc.perform(get("/api/v1/admin/dashboard/stats")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = "USER")
  void should_return_403_for_non_admin_role() throws Exception {
    mockMvc.perform(get("/api/v1/admin/dashboard/stats")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void should_allow_admin_role() throws Exception {
    org.mockito.Mockito.when(adminApplicationService.getDashboardStats()).thenReturn(java.util.Map.of());
    mockMvc.perform(get("/api/v1/admin/dashboard/stats")).andExpect(status().isOk());
  }
}
