package com.lzbsdsg.stocksimulation.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lzbsdsg.stocksimulation.auth.application.AuthApplicationService;
import com.lzbsdsg.stocksimulation.auth.application.command.LoginByOtpCommand;
import com.lzbsdsg.stocksimulation.auth.application.command.LoginCommand;
import com.lzbsdsg.stocksimulation.auth.application.command.RefreshTokenCommand;
import com.lzbsdsg.stocksimulation.auth.application.command.RegisterCommand;
import com.lzbsdsg.stocksimulation.auth.application.command.ResetPasswordCommand;
import com.lzbsdsg.stocksimulation.auth.application.command.SendOtpCommand;
import com.lzbsdsg.stocksimulation.auth.application.dto.TokenDTO;
import com.lzbsdsg.stocksimulation.auth.infrastructure.gateway.JwtTokenProvider;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** 认证控制器 API 测试。 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(
    properties = {
      "DEV_BASIC_AUTH_PASSWORD=test",
      "JWT_SECRET=test-secret-key-for-auth-module",
      "DB_PASSWORD=test",
      "RABBITMQ_PASSWORD=test"
    })
public class AuthControllerApiTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockBean private AuthApplicationService authApplicationService;
  @MockBean private JwtTokenProvider jwtTokenProvider;

  @Test
  void should_send_otp_success() throws Exception {
    doNothing().when(authApplicationService).sendOtp(any(SendOtpCommand.class));
    mockMvc
        .perform(
            post("/api/v1/auth/otp/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SendOtpCommand("u@test.com"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(200));
  }

  @Test
  void should_register_success() throws Exception {
    when(authApplicationService.register(any(RegisterCommand.class)))
        .thenReturn(new TokenDTO("a", "r", 1800, 1L, "nick"));
    RegisterCommand command =
        new RegisterCommand("u@test.com", "123456", "Strong123", "nick", new BigDecimal("10000"));
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(command)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accessToken").value("a"));
  }

  @Test
  void should_register_bad_request_for_invalid_email() throws Exception {
    RegisterCommand command =
        new RegisterCommand("bad", "123456", "Strong123", "nick", new BigDecimal("10000"));
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(command)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void should_login_success() throws Exception {
    when(authApplicationService.login(any(LoginCommand.class)))
        .thenReturn(new TokenDTO("a", "r", 1800, 1L, "nick"));
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new LoginCommand("u@test.com", "Strong123"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.refreshToken").value("r"));
  }

  @Test
  void should_login_otp_success() throws Exception {
    when(authApplicationService.loginByOtp(any(LoginByOtpCommand.class)))
        .thenReturn(new TokenDTO("a", "r", 1800, 1L, "nick"));
    mockMvc
        .perform(
            post("/api/v1/auth/login/otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new LoginByOtpCommand("u@test.com", "123456"))))
        .andExpect(status().isOk());
  }

  @Test
  void should_refresh_success() throws Exception {
    when(authApplicationService.refreshToken(any(RefreshTokenCommand.class)))
        .thenReturn(new TokenDTO("a2", "r2", 1800, 1L, "nick"));
    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshTokenCommand("refresh"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accessToken").value("a2"));
  }

  @Test
  void should_logout_success() throws Exception {
    doNothing().when(authApplicationService).logout(any());
    mockMvc
        .perform(post("/api/v1/auth/logout").header("Authorization", "Bearer abc"))
        .andExpect(status().isOk());
  }

  @Test
  void should_logout_success_when_missing_authorization_header() throws Exception {
    doNothing().when(authApplicationService).logout(any());
    mockMvc.perform(post("/api/v1/auth/logout")).andExpect(status().isOk());
  }

  @Test
  void should_reset_password_success() throws Exception {
    doNothing().when(authApplicationService).resetPassword(any(ResetPasswordCommand.class));
    mockMvc
        .perform(
            post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new ResetPasswordCommand("u@test.com", "123456", "Strong123"))))
        .andExpect(status().isOk());
  }
}
