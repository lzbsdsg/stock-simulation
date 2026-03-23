package com.lzbsdsg.stocksimulation.auth.application.command;

import jakarta.validation.constraints.NotBlank;

/** 刷新 Token 命令 */
public record RefreshTokenCommand(@NotBlank(message = "refreshToken不能为空") String refreshToken) {}
