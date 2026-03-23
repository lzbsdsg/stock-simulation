package com.lzbsdsg.stocksimulation.auth.application.command;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** 密码登录命令 */
public record LoginCommand(
    @NotBlank(message = "邮箱不能为空") @Email(message = "邮箱格式不正确") String email,
    @NotBlank(message = "密码不能为空") String password) {}
