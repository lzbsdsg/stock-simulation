package com.lzbsdsg.stocksimulation.auth.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 刷新 Token 命令 */
public record RefreshTokenCommand(
    @Schema(description = "刷新令牌", example = "eyJhbGciOiJIUzI1NiJ9.refresh-token-demo")
        @NotBlank(message = "refreshToken不能为空")
        String refreshToken) {}
