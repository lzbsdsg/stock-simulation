package com.lzbsdsg.stocksimulation.auth.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** 密码登录命令 */
public record LoginCommand(
        @Schema(description = "邮箱地址", example = "lzb05101115@gmail.com") @NotBlank(message = "邮箱不能为空") @Email(message = "邮箱格式不正确") String email,
        @Schema(description = "登录密码", example = "NewStrong123") @NotBlank(message = "密码不能为空") String password) {
}
