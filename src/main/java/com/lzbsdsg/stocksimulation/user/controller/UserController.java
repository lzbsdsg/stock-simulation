package com.lzbsdsg.stocksimulation.user.controller;

import com.lzbsdsg.stocksimulation.common.exception.BizException;
import com.lzbsdsg.stocksimulation.common.result.ErrorCode;
import com.lzbsdsg.stocksimulation.common.result.Result;
import com.lzbsdsg.stocksimulation.user.application.UserApplicationService;
import com.lzbsdsg.stocksimulation.user.application.command.ChangePasswordCommand;
import com.lzbsdsg.stocksimulation.user.application.command.UpdateUserProfileCommand;
import com.lzbsdsg.stocksimulation.user.application.dto.UserProfileDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/** 用户控制器 */
@Tag(name = "用户模块")
@RestController
@RequestMapping("/api/v1/user")
public class UserController {

  private final UserApplicationService userApplicationService;

  public UserController(UserApplicationService userApplicationService) {
    this.userApplicationService = userApplicationService;
  }

  @Operation(summary = "获取当前用户信息")
  @GetMapping("/me")
  public Result<UserProfileDTO> getCurrentUser() {
    UserProfileDTO profile = userApplicationService.getCurrentUser(currentUserId());
    return Result.success(profile);
  }

  @Operation(summary = "修改当前用户资料")
  @PutMapping("/me")
  public Result<UserProfileDTO> updateCurrentUser(
      @Valid @RequestBody UpdateUserProfileCommand command) {
    UserProfileDTO profile = userApplicationService.updateProfile(currentUserId(), command);
    return Result.success(profile);
  }

  @Operation(summary = "修改密码")
  @PutMapping("/password")
  public Result<Void> changePassword(@Valid @RequestBody ChangePasswordCommand command) {
    userApplicationService.changePassword(currentUserId(), command);
    return Result.success();
  }

  private Long currentUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || authentication.getPrincipal() == null) {
      throw new BizException(ErrorCode.UNAUTHORIZED);
    }
    try {
      return Long.parseLong(String.valueOf(authentication.getPrincipal()));
    } catch (NumberFormatException ex) {
      throw new BizException(ErrorCode.UNAUTHORIZED, "无效的用户身份");
    }
  }
}
