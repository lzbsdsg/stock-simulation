package com.lzbsdsg.stocksimulation.notification.domain.repository;

import com.lzbsdsg.stocksimulation.notification.domain.entity.NotificationMessage;
import java.util.List;
import java.util.Optional;

/** 通知仓储接口（domain 层定义） */
public interface NotificationRepository {

  void save(NotificationMessage message);

  Optional<NotificationMessage> findById(Long id);

  List<NotificationMessage> findByUserId(Long userId, int page, int size);

  long countByUserId(Long userId);

  long countUnreadByUserId(Long userId);

  void markAsRead(Long id);

  void markAllAsReadByUserId(Long userId);
}
