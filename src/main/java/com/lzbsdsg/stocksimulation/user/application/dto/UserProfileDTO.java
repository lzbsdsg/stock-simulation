package com.lzbsdsg.stocksimulation.user.application.dto;

/** 当前用户资料返回对象。 */
public record UserProfileDTO(
    Long userId, String email, String nickname, String role, String status) {}
