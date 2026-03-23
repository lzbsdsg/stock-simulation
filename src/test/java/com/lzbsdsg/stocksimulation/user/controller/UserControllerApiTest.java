package com.lzbsdsg.stocksimulation.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lzbsdsg.stocksimulation.auth.infrastructure.gateway.JwtTokenProvider;
import com.lzbsdsg.stocksimulation.user.application.UserApplicationService;
import com.lzbsdsg.stocksimulation.user.application.command.ChangePasswordCommand;
import com.lzbsdsg.stocksimulation.user.application.command.UpdateUserProfileCommand;
import com.lzbsdsg.stocksimulation.user.application.dto.UserProfileDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(
    properties = {
      "DEV_BASIC_AUTH_PASSWORD=test",
      "JWT_SECRET=test-secret-key-for-user-module",
      "DB_PASSWORD=test",
      "RABBITMQ_PASSWORD=test"
    })
class UserControllerApiTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockBean private UserApplicationService userApplicationService;
  @MockBean private JwtTokenProvider jwtTokenProvider;

  @BeforeEach
  void setUpAuth() {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken("1", null));
  }

  @AfterEach
  void clearAuth() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void should_get_current_user() throws Exception {
    when(userApplicationService.getCurrentUser(1L))
        .thenReturn(new UserProfileDTO(1L, "u@test.com", "nick", null, "USER", "ACTIVE"));

    mockMvc
        .perform(get("/api/v1/user/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(200))
        .andExpect(jsonPath("$.data.userId").value(1))
        .andExpect(jsonPath("$.data.email").value("u@test.com"));
  }

  @Test
  void should_update_current_user() throws Exception {
    UpdateUserProfileCommand command = new UpdateUserProfileCommand("newNick", "https://a.b/c.png");
    when(userApplicationService.updateProfile(eq(1L), any(UpdateUserProfileCommand.class)))
        .thenReturn(new UserProfileDTO(1L, "u@test.com", "newNick", "https://a.b/c.png", "USER", "ACTIVE"));

    mockMvc
        .perform(
            put("/api/v1/user/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(command)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.nickname").value("newNick"));
  }

  @Test
  void should_change_password() throws Exception {
    doNothing().when(userApplicationService).changePassword(eq(1L), any(ChangePasswordCommand.class));

    mockMvc
        .perform(
            put("/api/v1/user/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new ChangePasswordCommand("Strong123", "NewStrong123"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(200));
  }
}
