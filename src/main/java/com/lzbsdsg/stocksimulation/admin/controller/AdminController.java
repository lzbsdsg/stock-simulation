package com.lzbsdsg.stocksimulation.admin.controller;

import com.lzbsdsg.stocksimulation.admin.application.AdminApplicationService;
import com.lzbsdsg.stocksimulation.common.result.PageResult;
import com.lzbsdsg.stocksimulation.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 管理后台控制器 */
@Tag(name = "管理后台接口")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

  private final AdminApplicationService adminApplicationService;

  @Operation(summary = "获取用户列表")
  @GetMapping("/users")
  public Result<PageResult<Map<String, Object>>> listUsers(
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
    return Result.success(adminApplicationService.listUsers(page, size));
  }

  @Operation(summary = "禁用/启用用户")
  @PutMapping("/users/{userId}/status")
  public Result<Void> toggleUserStatus(@PathVariable Long userId, @RequestParam String status) {
    adminApplicationService.toggleUserStatus(userId, status);
    return Result.success(null);
  }

  @Operation(summary = "获取系统统计数据")
  @GetMapping("/dashboard/stats")
  public Result<Map<String, Object>> getDashboardStats() {
    return Result.success(adminApplicationService.getDashboardStats());
  }

  @Operation(summary = "获取系统排行榜")
  @GetMapping("/leaderboard")
  public Result<PageResult<Map<String, Object>>> getLeaderboard(
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
    return Result.success(adminApplicationService.getLeaderboard(page, size));
  }
}
