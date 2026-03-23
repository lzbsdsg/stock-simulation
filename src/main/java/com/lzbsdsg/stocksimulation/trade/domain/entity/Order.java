package com.lzbsdsg.stocksimulation.trade.domain.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/** 委托订单（领域实体） */
@Data
public class Order {

  private Long id;

  /** 用户ID */
  private Long userId;

  /** 客户端幂等键 */
  private String clientOrderId;

  /** 股票代码 */
  private String stockCode;

  /** 股票名称 */
  private String stockName;

  /** 买卖方向 */
  private OrderSide side;

  /** 订单类型 */
  private OrderType orderType;

  /** 订单状态 */
  private OrderStatus status;

  /** 委托价格 */
  private BigDecimal price;

  /** 委托数量 */
  private Integer quantity;

  /** 已成交数量 */
  private Integer filledQuantity;

  /** 已成交金额 */
  private BigDecimal filledAmount;

  /** 手续费 */
  private BigDecimal commission;

  /** 冻结金额（买入时冻结资金） */
  private BigDecimal frozenAmount;

  /** 乐观锁版本 */
  private Integer version;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  /** 是否可撤单 */
  public boolean isCancellable() {
    return status == OrderStatus.PENDING || status == OrderStatus.PARTIAL_FILLED;
  }

  /** 剩余未成交数量 */
  public int remainingQuantity() {
    return quantity - (filledQuantity == null ? 0 : filledQuantity);
  }

  /** 部分成交 */
  public void fill(int qty, BigDecimal amount, BigDecimal fee) {
    this.filledQuantity = (this.filledQuantity == null ? 0 : this.filledQuantity) + qty;
    this.filledAmount =
        (this.filledAmount == null ? BigDecimal.ZERO : this.filledAmount).add(amount);
    this.commission = (this.commission == null ? BigDecimal.ZERO : this.commission).add(fee);
    if (this.filledQuantity >= this.quantity) {
      this.status = OrderStatus.FILLED;
    } else {
      this.status = OrderStatus.PARTIAL_FILLED;
    }
  }

  /** 撤单 */
  public void cancel() {
    this.status = OrderStatus.CANCELLED;
  }
}
