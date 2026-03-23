package com.lzbsdsg.stocksimulation.portfolio.controller;

import com.lzbsdsg.stocksimulation.common.result.PageResult;
import com.lzbsdsg.stocksimulation.common.result.Result;
import com.lzbsdsg.stocksimulation.portfolio.application.PortfolioApplicationService;
import com.lzbsdsg.stocksimulation.portfolio.application.vo.FundFlowVO;
import com.lzbsdsg.stocksimulation.portfolio.application.vo.OverviewVO;
import com.lzbsdsg.stocksimulation.portfolio.application.vo.PositionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 持仓与资产控制器 */
@Tag(name = "持仓与资产接口")
@RestController
@RequestMapping("/api/v1/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

  private final PortfolioApplicationService portfolioApplicationService;

  @Operation(summary = "获取资产总览")
  @GetMapping("/overview")
  public Result<OverviewVO> getOverview() {
    return Result.success(portfolioApplicationService.getOverview());
  }

  @Operation(summary = "获取持仓列表")
  @GetMapping("/positions")
  public Result<List<PositionVO>> getPositions() {
    return Result.success(portfolioApplicationService.getPositions());
  }

  @Operation(summary = "获取资金流水")
  @GetMapping("/fund-flows")
  public Result<PageResult<FundFlowVO>> getFundFlows(
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
    return Result.success(portfolioApplicationService.getFundFlows(page, size));
  }
}
