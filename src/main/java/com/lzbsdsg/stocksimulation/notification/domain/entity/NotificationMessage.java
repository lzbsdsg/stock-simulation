package com.lzbsdsg.stocksimulation.notification.domain.entity;

import java.time.LocalDateTime;
import lombok.Data;

/** 消息通知（领域实体） */
@Data
public class NotificationMessage {

  private Long id;

  private Long userId;

  /** 通知类型: TRADE_FILLED, TRADE_CANCELLED, SYSTEM, RISK_ALERT */
  private String type;

  private String title;

  private String content;

  /** 是否已读 */
  private Boolean read;

  private LocalDateTime createdAt;

  public void markAsRead() {
    this.read = true;
  }
}
