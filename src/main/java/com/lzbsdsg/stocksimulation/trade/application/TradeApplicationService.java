package com.lzbsdsg.stocksimulation.trade.application;

import com.lzbsdsg.stocksimulation.common.result.PageResult;
import com.lzbsdsg.stocksimulation.trade.application.command.CancelOrderCommand;
import com.lzbsdsg.stocksimulation.trade.application.command.PlaceOrderCommand;
import com.lzbsdsg.stocksimulation.trade.application.vo.OrderVO;
import com.lzbsdsg.stocksimulation.trade.application.vo.TradeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 交易应用服务
 *
 * <p>编排下单/撤单/查询流程，不包含业务规则。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradeApplicationService {

  // TODO: 注入 OrderDomainService, MatchEngine, AccountApplicationService, OrderRepository 等

  /**
   * 下单（买入/卖出）
   *
   * <p>流程： 1. 幂等校验（clientOrderId） 2. 校验交易时间 & 涨跌停 & 最小单位 3. 计算冻结金额 / 校验可卖数量 4. SELECT FOR UPDATE
   * 锁定账户 5. 扣减可用资金/冻结持仓 6. 插入 Order(PENDING) 7. 发送撮合消息到 MQ
   */
  @Transactional
  public OrderVO placeOrder(PlaceOrderCommand command) {
    // TODO: 实现下单流程
    throw new UnsupportedOperationException("placeOrder not implemented");
  }

  /** 撤单 */
  @Transactional
  public void cancelOrder(CancelOrderCommand command) {
    // TODO: 实现撤单流程
    throw new UnsupportedOperationException("cancelOrder not implemented");
  }

  /** 查询当日委托 */
  public PageResult<OrderVO> getTodayOrders(int page, int size) {
    // TODO: 查询当日委托列表
    throw new UnsupportedOperationException("getTodayOrders not implemented");
  }

  /** 查询历史委托 */
  public PageResult<OrderVO> getHistoryOrders(int page, int size) {
    // TODO: 查询历史委托列表
    throw new UnsupportedOperationException("getHistoryOrders not implemented");
  }

  /** 查询成交记录 */
  public PageResult<TradeVO> getTrades(int page, int size) {
    // TODO: 查询成交列表
    throw new UnsupportedOperationException("getTrades not implemented");
  }
}
