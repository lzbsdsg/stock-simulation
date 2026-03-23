package com.lzbsdsg.stocksimulation.notification.controller;

import com.lzbsdsg.stocksimulation.common.result.PageResult;
import com.lzbsdsg.stocksimulation.common.result.Result;
import com.lzbsdsg.stocksimulation.notification.application.NotificationApplicationService;
import com.lzbsdsg.stocksimulation.notification.application.vo.NotificationVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 消息通知控制器 */
@Tag(name = "消息通知接口")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

  private final NotificationApplicationService notificationApplicationService;

  @Operation(summary = "获取通知列表")
  @GetMapping
  public Result<PageResult<NotificationVO>> getNotifications(
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
    return Result.success(notificationApplicationService.getNotifications(page, size));
  }

  @Operation(summary = "标记通知已读")
  @PutMapping("/{notificationId}/read")
  public Result<Void> markAsRead(@PathVariable Long notificationId) {
    notificationApplicationService.markAsRead(notificationId);
    return Result.success(null);
  }

  @Operation(summary = "标记全部已读")
  @PutMapping("/read-all")
  public Result<Void> markAllAsRead() {
    notificationApplicationService.markAllAsRead();
    return Result.success(null);
  }

  @Operation(summary = "获取未读数量")
  @GetMapping("/unread-count")
  public Result<Long> getUnreadCount() {
    return Result.success(notificationApplicationService.getUnreadCount());
  }
}
