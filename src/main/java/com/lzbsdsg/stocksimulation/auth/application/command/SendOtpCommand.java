package com.lzbsdsg.stocksimulation.auth.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** 发送验证码命令 */
public record SendOtpCommand(
    @Schema(description = "邮箱地址", example = "lzb05101115@gmail.com")
        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        String email) {}
