package com.lzbsdsg.stocksimulation.notification.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** 通知消息 Mapper */
@Mapper
public interface NotificationMapper extends BaseMapper<NotificationDO> {}
