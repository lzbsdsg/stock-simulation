package com.lzbsdsg.stocksimulation.user.controller;

import com.lzbsdsg.stocksimulation.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/** 用户控制器 */
@Tag(name = "用户模块")
@RestController
@RequestMapping("/api/v1/user")
public class UserController {

  // TODO: 注入 UserApplicationService

  @Operation(summary = "获取当前用户信息")
  @GetMapping("/me")
  public Result<?> getCurrentUser() {
    // TODO: 从 SecurityContext 获取用户ID → 查询用户信息
    return Result.success();
  }

  @Operation(summary = "修改密码")
  @PutMapping("/password")
  public Result<Void> changePassword() {
    // TODO: 校验旧密码 → 校验新密码强度 → 更新密码
    return Result.success();
  }
}
