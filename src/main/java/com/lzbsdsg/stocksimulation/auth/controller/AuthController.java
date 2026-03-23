package com.lzbsdsg.stocksimulation.auth.controller;

import com.lzbsdsg.stocksimulation.auth.application.AuthApplicationService;
import com.lzbsdsg.stocksimulation.auth.application.command.*;
import com.lzbsdsg.stocksimulation.auth.application.dto.TokenDTO;
import com.lzbsdsg.stocksimulation.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** 认证控制器 */
@Tag(name = "认证模块")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthApplicationService authApplicationService;

  public AuthController(AuthApplicationService authApplicationService) {
    this.authApplicationService = authApplicationService;
  }

  @Operation(summary = "发送邮箱验证码")
  @PostMapping("/otp/send")
  public Result<Void> sendOtp(@Valid @RequestBody SendOtpCommand command) {
    authApplicationService.sendOtp(command);
    return Result.success();
  }

  @Operation(summary = "邮箱验证码注册")
  @PostMapping("/register")
  public Result<TokenDTO> register(@Valid @RequestBody RegisterCommand command) {
    TokenDTO token = authApplicationService.register(command);
    return Result.success(token);
  }

  @Operation(summary = "密码登录")
  @PostMapping("/login")
  public Result<TokenDTO> login(@Valid @RequestBody LoginCommand command) {
    TokenDTO token = authApplicationService.login(command);
    return Result.success(token);
  }

  @Operation(summary = "验证码登录")
  @PostMapping("/login/otp")
  public Result<TokenDTO> loginByOtp(@Valid @RequestBody LoginByOtpCommand command) {
    TokenDTO token = authApplicationService.loginByOtp(command);
    return Result.success(token);
  }

  @Operation(summary = "刷新Token")
  @PostMapping("/refresh")
  public Result<TokenDTO> refresh(@Valid @RequestBody RefreshTokenCommand command) {
    TokenDTO token = authApplicationService.refreshToken(command);
    return Result.success(token);
  }

  @Operation(summary = "登出")
  @PostMapping("/logout")
  public Result<Void> logout(@RequestHeader("Authorization") String authorization) {
    authApplicationService.logout(authorization);
    return Result.success();
  }

  @Operation(summary = "忘记密码 — 发送重置验证码")
  @PostMapping("/forgot-password")
  public Result<Void> forgotPassword(@Valid @RequestBody SendOtpCommand command) {
    authApplicationService.sendOtp(command);
    return Result.success();
  }

  @Operation(summary = "重置密码")
  @PostMapping("/reset-password")
  public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordCommand command) {
    authApplicationService.resetPassword(command);
    return Result.success();
  }
}
