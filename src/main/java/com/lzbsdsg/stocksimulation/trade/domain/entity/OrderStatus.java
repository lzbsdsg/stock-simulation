package com.lzbsdsg.stocksimulation.trade.domain.entity;

/** 订单状态枚举 */
public enum OrderStatus {

  /** 待成交 */
  PENDING,

  /** 部分成交 */
  PARTIAL_FILLED,

  /** 全部成交 */
  FILLED,

  /** 已撤单 */
  CANCELLED,

  /** 已拒绝（校验不通过） */
  REJECTED,

  /** 已过期（收盘未成交自动撤销） */
  EXPIRED;
}
