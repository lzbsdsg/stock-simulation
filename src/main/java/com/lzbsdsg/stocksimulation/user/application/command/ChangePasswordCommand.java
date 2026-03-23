package com.lzbsdsg.stocksimulation.user.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 修改密码命令。 */
public record ChangePasswordCommand(
    @Schema(description = "旧密码", example = "Strong123") @NotBlank(message = "旧密码不能为空")
        String oldPassword,
    @Schema(description = "新密码（至少8位，含大小写和数字）", example = "NewStrong123")
        @NotBlank(message = "新密码不能为空")
        @Size(min = 8, message = "新密码至少8位")
        String newPassword) {}
