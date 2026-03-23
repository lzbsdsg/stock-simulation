package com.lzbsdsg.stocksimulation.auth.application.command;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** 发送验证码命令 */
public record SendOtpCommand(
    @NotBlank(message = "邮箱不能为空") @Email(message = "邮箱格式不正确") String email) {}
