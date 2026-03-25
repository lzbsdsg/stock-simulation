package com.lzbsdsg.stocksimulation.notification.application;

import com.lzbsdsg.stocksimulation.common.exception.BizException;
import com.lzbsdsg.stocksimulation.common.result.ErrorCode;
import com.lzbsdsg.stocksimulation.common.result.PageResult;
import com.lzbsdsg.stocksimulation.notification.application.vo.NotificationVO;
import com.lzbsdsg.stocksimulation.notification.domain.entity.NotificationMessage;
import com.lzbsdsg.stocksimulation.notification.domain.repository.NotificationRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** 消息通知应用服务 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationApplicationService {

  private static final int DEFAULT_PAGE = 1;
  private static final int DEFAULT_SIZE = 20;
  private static final int MAX_SIZE = 100;

  private final NotificationRepository notificationRepository;
  private final ObjectProvider<SimpMessagingTemplate> simpMessagingTemplateProvider;

  public PageResult<NotificationVO> getNotifications(int page, int size) {
    Long userId = currentUserId();
    int safePage = page < 1 ? DEFAULT_PAGE : page;
    int safeSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    List<NotificationVO> records =
        notificationRepository.findByUserId(userId, safePage, safeSize).stream()
            .map(this::toVO)
            .toList();
    long total = notificationRepository.countByUserId(userId);
    return new PageResult<>(records, total, safePage, safeSize);
  }

  public void markAsRead(Long notificationId) {
    Long userId = currentUserId();
    NotificationMessage message =
        notificationRepository
            .findById(notificationId)
            .orElseThrow(() -> new BizException(ErrorCode.NOTIFICATION_NOT_FOUND));
    if (!userId.equals(message.getUserId())) {
      throw new BizException(ErrorCode.FORBIDDEN);
    }
    notificationRepository.markAsRead(notificationId);
  }

  public void markAllAsRead() {
    notificationRepository.markAllAsReadByUserId(currentUserId());
  }

  public Long getUnreadCount() {
    return notificationRepository.countUnreadByUserId(currentUserId());
  }

  /** 发送系统通知（内部调用） */
  public void sendNotification(Long userId, String title, String content, String type) {
    NotificationMessage message = new NotificationMessage();
    message.setUserId(userId);
    message.setTitle(title);
    message.setContent(content);
    message.setType(type);
    message.setRead(false);
    message.setCreatedAt(LocalDateTime.now());
    notificationRepository.save(message);

    NotificationVO vo = toVO(message);
    SimpMessagingTemplate simpMessagingTemplate = simpMessagingTemplateProvider.getIfAvailable();
    if (simpMessagingTemplate != null) {
      simpMessagingTemplate.convertAndSendToUser(String.valueOf(userId), "/queue/notification", vo);
    }
  }

  private NotificationVO toVO(NotificationMessage message) {
    return new NotificationVO(
        message.getId(),
        message.getType(),
        message.getTitle(),
        message.getContent(),
        message.getRead(),
        message.getCreatedAt());
  }

  private Long currentUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || authentication instanceof AnonymousAuthenticationToken
        || authentication.getPrincipal() == null) {
      throw new BizException(ErrorCode.UNAUTHORIZED);
    }
    try {
      return Long.parseLong(String.valueOf(authentication.getPrincipal()));
    } catch (NumberFormatException ex) {
      throw new BizException(ErrorCode.UNAUTHORIZED);
    }
  }
}
