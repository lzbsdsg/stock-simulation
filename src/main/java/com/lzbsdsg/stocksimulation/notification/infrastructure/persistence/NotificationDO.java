package com.lzbsdsg.stocksimulation.notification.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 通知消息 DO */
@Data
@TableName("t_notification_message")
public class NotificationDO {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long userId;

  private String type;

  private String title;

  private String content;

  private Boolean read;

  private LocalDateTime createdAt;
}
