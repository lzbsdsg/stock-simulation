package com.lzbsdsg.stocksimulation.portfolio.domain.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/** 持仓（领域实体） */
@Data
public class Position {

  private Long id;

  private Long userId;

  private String stockCode;

  private String stockName;

  /** 总数量 */
  private Integer totalQuantity;

  /** 可卖数量 */
  private Integer availableQuantity;

  /** 冻结数量（T+1 未解冻） */
  private Integer frozenQuantity;

  /** 成本价 */
  private BigDecimal costPrice;

  /** 成本总额 */
  private BigDecimal totalCost;

  /** T+1 冻结截止日期 */
  private LocalDate frozenUntil;

  /** 乐观锁版本 */
  private Integer version;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  /** 买入加仓：更新成本价（加权平均） */
  public void addPosition(int qty, BigDecimal price) {
    BigDecimal newCost = price.multiply(BigDecimal.valueOf(qty));
    this.totalCost = (this.totalCost == null ? BigDecimal.ZERO : this.totalCost).add(newCost);
    this.totalQuantity = (this.totalQuantity == null ? 0 : this.totalQuantity) + qty;
    this.frozenQuantity = (this.frozenQuantity == null ? 0 : this.frozenQuantity) + qty;
    // 重新计算加权平均成本价
    if (this.totalQuantity > 0) {
      this.costPrice =
          this.totalCost.divide(BigDecimal.valueOf(this.totalQuantity), 4, RoundingMode.HALF_UP);
    }
  }

  /** 卖出减仓 */
  public void reducePosition(int qty) {
    this.availableQuantity -= qty;
    this.totalQuantity -= qty;
    if (this.totalQuantity > 0) {
      this.totalCost = this.costPrice.multiply(BigDecimal.valueOf(this.totalQuantity));
    } else {
      this.totalCost = BigDecimal.ZERO;
      this.costPrice = BigDecimal.ZERO;
    }
  }

  /** T+1 解冻 */
  public void unfreezeIfDue(LocalDate today) {
    if (frozenUntil != null && !today.isBefore(frozenUntil)) {
      this.availableQuantity =
          (this.availableQuantity == null ? 0 : this.availableQuantity) + this.frozenQuantity;
      this.frozenQuantity = 0;
      this.frozenUntil = null;
    }
  }

  /** 是否清仓 */
  public boolean isCleared() {
    return totalQuantity == null || totalQuantity == 0;
  }
}
