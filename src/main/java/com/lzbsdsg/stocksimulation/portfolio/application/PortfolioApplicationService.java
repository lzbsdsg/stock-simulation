package com.lzbsdsg.stocksimulation.portfolio.application;

import com.lzbsdsg.stocksimulation.common.result.PageResult;
import com.lzbsdsg.stocksimulation.portfolio.application.vo.FundFlowVO;
import com.lzbsdsg.stocksimulation.portfolio.application.vo.OverviewVO;
import com.lzbsdsg.stocksimulation.portfolio.application.vo.PositionVO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 持仓与资产应用服务 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioApplicationService {

  // TODO: 注入 PositionRepository, AccountRepository, FundFlowRepository, MarketDataFacade

  /**
   * 获取资产总览
   *
   * <p>总资产 = 可用资金 + 冻结资金 + 持仓市值
   */
  public OverviewVO getOverview() {
    // TODO: 实现资产总览查询
    throw new UnsupportedOperationException("getOverview not implemented");
  }

  /** 获取持仓列表（含实时盈亏） */
  public List<PositionVO> getPositions() {
    // TODO: 查询所有持仓 + 实时行情计算盈亏
    throw new UnsupportedOperationException("getPositions not implemented");
  }

  /** 获取资金流水 */
  public PageResult<FundFlowVO> getFundFlows(int page, int size) {
    // TODO: 查询资金流水记录
    throw new UnsupportedOperationException("getFundFlows not implemented");
  }
}
