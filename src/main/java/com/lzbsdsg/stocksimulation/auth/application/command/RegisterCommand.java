package com.lzbsdsg.stocksimulation.auth.application.command;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/** 注册命令 */
public record RegisterCommand(
    @NotBlank(message = "邮箱不能为空") @Email(message = "邮箱格式不正确") String email,
    @NotBlank(message = "验证码不能为空") @Size(min = 6, max = 6, message = "验证码为6位数字") String otp,
    @NotBlank(message = "密码不能为空") @Size(min = 8, message = "密码至少8位") String password,
    @NotBlank(message = "昵称不能为空") @Size(min = 1, max = 50, message = "昵称长度1-50") String nickname,
    @NotNull(message = "初始资金不能为空")
        @DecimalMin(value = "10000", message = "初始资金最低10000")
        @DecimalMax(value = "1000000", message = "初始资金最高1000000")
        BigDecimal initialBalance) {}
