package com.lzbsdsg.stocksimulation.notification.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lzbsdsg.stocksimulation.notification.domain.entity.NotificationMessage;
import com.lzbsdsg.stocksimulation.notification.domain.repository.NotificationRepository;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 通知仓储实现 */
@Repository
@RequiredArgsConstructor
public class NotificationRepositoryImpl implements NotificationRepository {

  private static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");

  private final NotificationMapper notificationMapper;

  @Override
  public void save(NotificationMessage message) {
    NotificationDO d = toDO(message);
    notificationMapper.insert(d);
    message.setId(d.getId());
  }

  @Override
  public Optional<NotificationMessage> findById(Long id) {
    NotificationDO d = notificationMapper.selectById(id);
    return Optional.ofNullable(d).map(this::toDomain);
  }

  @Override
  public List<NotificationMessage> findByUserId(Long userId, int page, int size) {
    Page<NotificationDO> p =
        notificationMapper.selectPage(
            new Page<>(page, size),
            new LambdaQueryWrapper<NotificationDO>()
                .eq(NotificationDO::getUserId, userId)
                .orderByDesc(NotificationDO::getCreatedAt));
    return p.getRecords().stream().map(this::toDomain).collect(Collectors.toList());
  }

  @Override
  public long countByUserId(Long userId) {
    return notificationMapper.selectCount(
        new LambdaQueryWrapper<NotificationDO>().eq(NotificationDO::getUserId, userId));
  }

  @Override
  public long countUnreadByUserId(Long userId) {
    return notificationMapper.selectCount(
        new LambdaQueryWrapper<NotificationDO>()
            .eq(NotificationDO::getUserId, userId)
            .eq(NotificationDO::getRead, false));
  }

  @Override
  public void markAsRead(Long id) {
    notificationMapper.update(
        null,
        new LambdaUpdateWrapper<NotificationDO>()
            .eq(NotificationDO::getId, id)
            .set(NotificationDO::getRead, true));
  }

  @Override
  public void markAllAsReadByUserId(Long userId) {
    notificationMapper.update(
        null,
        new LambdaUpdateWrapper<NotificationDO>()
            .eq(NotificationDO::getUserId, userId)
            .eq(NotificationDO::getRead, false)
            .set(NotificationDO::getRead, true));
  }

  // ---- Converter ----

  private NotificationMessage toDomain(NotificationDO d) {
    NotificationMessage m = new NotificationMessage();
    m.setId(d.getId());
    m.setUserId(d.getUserId());
    m.setType(d.getType());
    m.setTitle(d.getTitle());
    m.setContent(d.getContent());
    m.setRead(d.getRead());
    m.setCreatedAt(
        d.getCreatedAt() == null
            ? null
            : d.getCreatedAt().atZoneSameInstant(ZONE_SHANGHAI).toLocalDateTime());
    return m;
  }

  private NotificationDO toDO(NotificationMessage m) {
    NotificationDO d = new NotificationDO();
    d.setId(m.getId());
    d.setUserId(m.getUserId());
    d.setType(m.getType());
    d.setTitle(m.getTitle());
    d.setContent(m.getContent());
    d.setRead(m.getRead());
    d.setCreatedAt(
        m.getCreatedAt() == null
            ? null
            : m.getCreatedAt().atZone(ZONE_SHANGHAI).toOffsetDateTime());
    return d;
  }
}
