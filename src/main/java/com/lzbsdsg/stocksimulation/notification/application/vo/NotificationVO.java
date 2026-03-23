package com.lzbsdsg.stocksimulation.notification.application.vo;

import java.time.LocalDateTime;

/** 通知 VO */
public record NotificationVO(
    Long notificationId,
    /** 通知类型: TRADE_FILLED, TRADE_CANCELLED, SYSTEM, RISK_ALERT */
    String type,
    String title,
    String content,
    Boolean read,
    LocalDateTime createdAt) {}
