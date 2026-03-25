package com.lzbsdsg.stocksimulation.trade.domain.repository;

import com.lzbsdsg.stocksimulation.trade.domain.entity.Order;
import com.lzbsdsg.stocksimulation.trade.domain.entity.OrderStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 订单仓储接口（domain 层定义） */
public interface OrderRepository {

  Optional<Order> findById(Long orderId);

  Optional<Order> findByClientOrderId(String clientOrderId);

  List<Order> findByUserIdAndCreatedAtBetween(
      Long userId, LocalDateTime from, LocalDateTime to, int page, int size);

  List<Order> findByUserIdAndStatus(Long userId, OrderStatus status);

  long countByUserIdAndCreatedAtBetween(Long userId, LocalDateTime from, LocalDateTime to);

  List<Order> findPendingOrders();

  int archiveClosedOrdersWithoutTrades(LocalDateTime cutoff, int batchSize);

  void save(Order order);

  boolean updateWithVersion(Order order);
}
