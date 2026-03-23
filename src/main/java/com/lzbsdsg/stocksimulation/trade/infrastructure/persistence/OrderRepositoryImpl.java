package com.lzbsdsg.stocksimulation.trade.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lzbsdsg.stocksimulation.trade.domain.entity.*;
import com.lzbsdsg.stocksimulation.trade.domain.repository.OrderRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 订单仓储实现 */
@Repository
@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepository {

  private final OrderMapper orderMapper;

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
    Page<OrderDO> p =
        orderMapper.selectPage(
            new Page<>(page, size),
            new LambdaQueryWrapper<OrderDO>()
                .eq(OrderDO::getUserId, userId)
                .between(OrderDO::getCreatedAt, from, to)
                .orderByDesc(OrderDO::getCreatedAt));
    return p.getRecords().stream().map(this::toDomain).collect(Collectors.toList());
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
    return orderMapper.selectCount(
        new LambdaQueryWrapper<OrderDO>()
            .eq(OrderDO::getUserId, userId)
            .between(OrderDO::getCreatedAt, from, to));
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

  // ---- Converter ----

  private Order toDomain(OrderDO d) {
    Order o = new Order();
    o.setId(d.getId());
    o.setUserId(d.getUserId());
    o.setClientOrderId(d.getClientOrderId());
    o.setStockCode(d.getStockCode());
    o.setStockName(d.getStockName());
    o.setSide(OrderSide.valueOf(d.getSide()));
    o.setOrderType(OrderType.valueOf(d.getOrderType()));
    o.setStatus(OrderStatus.valueOf(d.getStatus()));
    o.setPrice(d.getPrice());
    o.setQuantity(d.getQuantity());
    o.setFilledQuantity(d.getFilledQuantity());
    o.setFilledAmount(d.getFilledAmount());
    o.setCommission(d.getCommission());
    o.setFrozenAmount(d.getFrozenAmount());
    o.setVersion(d.getVersion());
    o.setCreatedAt(d.getCreatedAt());
    o.setUpdatedAt(d.getUpdatedAt());
    return o;
  }

  private OrderDO toDO(Order o) {
    OrderDO d = new OrderDO();
    d.setId(o.getId());
    d.setUserId(o.getUserId());
    d.setClientOrderId(o.getClientOrderId());
    d.setStockCode(o.getStockCode());
    d.setStockName(o.getStockName());
    d.setSide(o.getSide().name());
    d.setOrderType(o.getOrderType().name());
    d.setStatus(o.getStatus().name());
    d.setPrice(o.getPrice());
    d.setQuantity(o.getQuantity());
    d.setFilledQuantity(o.getFilledQuantity());
    d.setFilledAmount(o.getFilledAmount());
    d.setCommission(o.getCommission());
    d.setFrozenAmount(o.getFrozenAmount());
    d.setVersion(o.getVersion());
    d.setCreatedAt(o.getCreatedAt());
    d.setUpdatedAt(o.getUpdatedAt());
    return d;
  }
}
