package com.lzbsdsg.stocksimulation.auth.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 重置密码命令 */
public record ResetPasswordCommand(
    @Schema(description = "邮箱地址", example = "lzb05101115@gmail.com")
        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        String email,
    @Schema(description = "6位验证码", example = "123456")
        @NotBlank(message = "验证码不能为空")
        @Size(min = 6, max = 6, message = "验证码为6位数字")
        String otp,
    @Schema(description = "新密码（至少8位，含大小写和数字）", example = "NewStrong123")
        @NotBlank(message = "新密码不能为空")
        @Size(min = 8, message = "密码至少8位")
        String newPassword) {}
