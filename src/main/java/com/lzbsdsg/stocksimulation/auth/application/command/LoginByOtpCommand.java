package com.lzbsdsg.stocksimulation.auth.application.command;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** OTP 登录命令 */
public record LoginByOtpCommand(
    @NotBlank(message = "邮箱不能为空") @Email(message = "邮箱格式不正确") String email,
    @NotBlank(message = "验证码不能为空") @Size(min = 6, max = 6, message = "验证码为6位数字") String otp) {}
