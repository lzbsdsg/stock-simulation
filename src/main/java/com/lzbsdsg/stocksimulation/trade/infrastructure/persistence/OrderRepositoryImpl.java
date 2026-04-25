package com.lzbsdsg.stocksimulation.trade.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lzbsdsg.stocksimulation.trade.domain.entity.Order;
import com.lzbsdsg.stocksimulation.trade.domain.entity.OrderSide;
import com.lzbsdsg.stocksimulation.trade.domain.entity.OrderStatus;
import com.lzbsdsg.stocksimulation.trade.domain.entity.OrderType;
import com.lzbsdsg.stocksimulation.trade.domain.repository.OrderRepository;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 订单仓储实现 */
@Repository
@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepository {

  private static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");

  private final OrderMapper orderMapper;
  private final OrderArchiveMapper orderArchiveMapper;

  @Override
  public Optional<Order> findById(Long orderId) {
    OrderDO d = orderMapper.selectById(orderId);
    return Optional.ofNullable(d).map(this::toDomain);
  }

  @Override
  public Optional<Order> findByClientOrderId(String clientOrderId) {
    OrderDO d =
        orderMapper.selectOne(
            new LambdaQueryWrapper<OrderDO>().eq(OrderDO::getClientOrderId, clientOrderId));
    return Optional.ofNullable(d).map(this::toDomain);
  }

  @Override
  public List<Order> findByUserIdAndCreatedAtBetween(
      Long userId, LocalDateTime from, LocalDateTime to, int page, int size) {
    long offset = Math.max(page - 1L, 0L) * size;
    OffsetDateTime fromOffset = from.atZone(ZONE_SHANGHAI).toOffsetDateTime();
    OffsetDateTime toOffset = to.atZone(ZONE_SHANGHAI).toOffsetDateTime();
    return orderArchiveMapper
        .selectHistoryByUserIdAndCreatedAtBetween(userId, fromOffset, toOffset, size, offset)
        .stream()
        .map(this::toDomain)
        .collect(Collectors.toList());
  }

  @Override
  public List<Order> findActiveByUserIdAndCreatedAtBetween(
      Long userId, LocalDateTime from, LocalDateTime to, int page, int size) {
    OffsetDateTime fromOffset = from.atZone(ZONE_SHANGHAI).toOffsetDateTime();
    OffsetDateTime toOffset = to.atZone(ZONE_SHANGHAI).toOffsetDateTime();
    Page<OrderDO> result =
        orderMapper.selectPage(
            new Page<>(page, size),
            new LambdaQueryWrapper<OrderDO>()
                .eq(OrderDO::getUserId, userId)
                .between(OrderDO::getCreatedAt, fromOffset, toOffset)
                .orderByDesc(OrderDO::getCreatedAt)
                .orderByDesc(OrderDO::getId));
    return result.getRecords().stream().map(this::toDomain).collect(Collectors.toList());
  }

  @Override
  public List<Order> findByUserIdAndStatus(Long userId, OrderStatus status) {
    List<OrderDO> list =
        orderMapper.selectList(
            new LambdaQueryWrapper<OrderDO>()
                .eq(OrderDO::getUserId, userId)
                .eq(OrderDO::getStatus, status.name()));
    return list.stream().map(this::toDomain).collect(Collectors.toList());
  }

  @Override
  public long countByUserIdAndCreatedAtBetween(Long userId, LocalDateTime from, LocalDateTime to) {
    OffsetDateTime fromOffset = from.atZone(ZONE_SHANGHAI).toOffsetDateTime();
    OffsetDateTime toOffset = to.atZone(ZONE_SHANGHAI).toOffsetDateTime();
    return orderArchiveMapper.countHistoryByUserIdAndCreatedAtBetween(userId, fromOffset, toOffset);
  }

  @Override
  public long countActiveByUserIdAndCreatedAtBetween(
      Long userId, LocalDateTime from, LocalDateTime to) {
    OffsetDateTime fromOffset = from.atZone(ZONE_SHANGHAI).toOffsetDateTime();
    OffsetDateTime toOffset = to.atZone(ZONE_SHANGHAI).toOffsetDateTime();
    return orderMapper.selectCount(
        new LambdaQueryWrapper<OrderDO>()
            .eq(OrderDO::getUserId, userId)
            .between(OrderDO::getCreatedAt, fromOffset, toOffset));
  }

  @Override
  public List<Order> findPendingOrders() {
    List<OrderDO> list =
        orderMapper.selectList(
            new LambdaQueryWrapper<OrderDO>()
                .in(
                    OrderDO::getStatus,
                    OrderStatus.PENDING.name(),
                    OrderStatus.PARTIAL_FILLED.name()));
    return list.stream().map(this::toDomain).collect(Collectors.toList());
  }

  @Override
  public int archiveClosedOrdersWithoutTrades(LocalDateTime cutoff, int batchSize) {
    OffsetDateTime cutoffOffset = cutoff.atZone(ZONE_SHANGHAI).toOffsetDateTime();
    return orderArchiveMapper.archiveClosedOrdersWithoutTrades(cutoffOffset, batchSize);
  }

  @Override
  public void save(Order order) {
    OrderDO d = toDO(order);
    orderMapper.insert(d);
    order.setId(d.getId());
  }

  @Override
  public boolean updateWithVersion(Order order) {
    OrderDO d = toDO(order);
    int rows = orderMapper.updateById(d);
    return rows > 0;
  }

  private Order toDomain(OrderDO d) {
    Order o = new Order();
    o.setId(d.getId());
    o.setUserId(d.getUserId());
    o.setClientOrderId(d.getClientOrderId());
    o.setStockCode(d.getStockCode());
    o.setStockName(d.getStockName());
    o.setSide(enumFromDb(OrderSide.class, d.getSide()));
    o.setOrderType(enumFromDb(OrderType.class, d.getOrderType()));
    o.setStatus(enumFromDb(OrderStatus.class, d.getStatus()));
    o.setPrice(d.getPrice());
    o.setQuantity(d.getQuantity());
    o.setFilledQuantity(d.getFilledQuantity());
    o.setFilledAmount(d.getFilledAmount());
    o.setCommission(d.getCommission());
    o.setFrozenAmount(d.getFrozenAmount());
    o.setVersion(d.getVersion());
    o.setCreatedAt(
        d.getCreatedAt() == null
            ? null
            : d.getCreatedAt().atZoneSameInstant(ZONE_SHANGHAI).toLocalDateTime());
    o.setUpdatedAt(
        d.getUpdatedAt() == null
            ? null
            : d.getUpdatedAt().atZoneSameInstant(ZONE_SHANGHAI).toLocalDateTime());
    return o;
  }

  private OrderDO toDO(Order o) {
    OrderDO d = new OrderDO();
    d.setId(o.getId());
    d.setUserId(o.getUserId());
    d.setClientOrderId(o.getClientOrderId());
    d.setStockCode(o.getStockCode());
    d.setStockName(o.getStockName());
    d.setSide(enumToDb(o.getSide()));
    d.setOrderType(enumToDb(o.getOrderType()));
    d.setStatus(enumToDb(o.getStatus()));
    d.setPrice(o.getPrice());
    d.setQuantity(o.getQuantity());
    d.setFilledQuantity(o.getFilledQuantity());
    d.setFilledAmount(o.getFilledAmount());
    d.setCommission(o.getCommission());
    d.setFrozenAmount(o.getFrozenAmount());
    d.setVersion(o.getVersion());
    d.setCreatedAt(
        o.getCreatedAt() == null
            ? null
            : o.getCreatedAt().atZone(ZONE_SHANGHAI).toOffsetDateTime());
    d.setUpdatedAt(
        o.getUpdatedAt() == null
            ? null
            : o.getUpdatedAt().atZone(ZONE_SHANGHAI).toOffsetDateTime());
    return d;
  }

  private <E extends Enum<E>> E enumFromDb(Class<E> enumType, String rawValue) {
    if (rawValue == null || rawValue.isBlank()) {
      return null;
    }
    try {
      return Enum.valueOf(enumType, rawValue);
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  private String enumToDb(Enum<?> value) {
    return value == null ? null : value.name();
  }
}
