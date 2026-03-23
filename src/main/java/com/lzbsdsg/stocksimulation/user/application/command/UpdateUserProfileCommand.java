package com.lzbsdsg.stocksimulation.user.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 更新用户资料命令。 */
public record UpdateUserProfileCommand(
    @Schema(description = "昵称", example = "new_nickname")
        @NotBlank(message = "昵称不能为空")
        @Size(max = 50, message = "昵称最多50字符")
        String nickname,
    @Schema(description = "头像URL", example = "https://example.com/avatar.png")
        @Size(max = 500, message = "头像URL过长")
        String avatarUrl) {}
