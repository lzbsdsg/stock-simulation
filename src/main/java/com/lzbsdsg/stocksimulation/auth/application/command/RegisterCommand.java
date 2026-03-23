package com.lzbsdsg.stocksimulation.auth.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/** 注册命令 */
public record RegisterCommand(
    @Schema(description = "邮箱地址", example = "lzb05101115@gmail.com")
        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        String email,
    @Schema(description = "6位验证码", example = "NewString123")
        @NotBlank(message = "验证码不能为空")
        @Size(min = 6, max = 6, message = "验证码为6位数字")
        String otp,
    @Schema(description = "登录密码（至少8位，含大小写和数字）", example = "Strong123")
        @NotBlank(message = "密码不能为空")
        @Size(min = 8, message = "密码至少8位")
        String password,
    @Schema(description = "昵称", example = "tester")
        @NotBlank(message = "昵称不能为空")
        @Size(min = 1, max = 50, message = "昵称长度1-50")
        String nickname,
    @Schema(description = "初始资金", example = "10000")
        @NotNull(message = "初始资金不能为空")
        @DecimalMin(value = "10000", message = "初始资金最低10000")
        @DecimalMax(value = "1000000", message = "初始资金最高1000000")
        BigDecimal initialBalance) {}
