package com.lzbsdsg.stocksimulation.notification.application;

import com.lzbsdsg.stocksimulation.common.result.PageResult;
import com.lzbsdsg.stocksimulation.notification.application.vo.NotificationVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 消息通知应用服务 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationApplicationService {

  // TODO: 注入 NotificationRepository

  public PageResult<NotificationVO> getNotifications(int page, int size) {
    // TODO: 查询当前用户通知列表
    throw new UnsupportedOperationException("getNotifications not implemented");
  }

  public void markAsRead(Long notificationId) {
    // TODO: 标记通知已读
    throw new UnsupportedOperationException("markAsRead not implemented");
  }

  public void markAllAsRead() {
    // TODO: 标记全部已读
    throw new UnsupportedOperationException("markAllAsRead not implemented");
  }

  public Long getUnreadCount() {
    // TODO: 查询未读数量
    throw new UnsupportedOperationException("getUnreadCount not implemented");
  }

  /** 发送系统通知（内部调用） */
  public void sendNotification(Long userId, String title, String content, String type) {
    // TODO: 创建通知记录 + WebSocket 实时推送
  }
}
