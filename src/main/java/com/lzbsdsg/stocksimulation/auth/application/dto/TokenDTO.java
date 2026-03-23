package com.lzbsdsg.stocksimulation.auth.application.dto;

/** Token 响应 DTO */
public record TokenDTO(
    String accessToken, String refreshToken, long expiresIn, Long userId, String nickname) {}
