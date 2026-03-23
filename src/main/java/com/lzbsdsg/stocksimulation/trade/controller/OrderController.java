package com.lzbsdsg.stocksimulation.trade.controller;

import com.lzbsdsg.stocksimulation.common.result.PageResult;
import com.lzbsdsg.stocksimulation.common.result.Result;
import com.lzbsdsg.stocksimulation.trade.application.TradeApplicationService;
import com.lzbsdsg.stocksimulation.trade.application.command.CancelOrderCommand;
import com.lzbsdsg.stocksimulation.trade.application.command.PlaceOrderCommand;
import com.lzbsdsg.stocksimulation.trade.application.vo.OrderVO;
import com.lzbsdsg.stocksimulation.trade.application.vo.TradeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 交易控制器 */
@Tag(name = "交易接口")
@RestController
@RequestMapping("/api/v1/trade")
@RequiredArgsConstructor
public class OrderController {

  private final TradeApplicationService tradeApplicationService;

  @Operation(summary = "下单（买入/卖出）")
  @PostMapping("/order")
  public Result<OrderVO> placeOrder(@Valid @RequestBody PlaceOrderCommand command) {
    return Result.success(tradeApplicationService.placeOrder(command));
  }

  @Operation(summary = "撤单")
  @PostMapping("/order/cancel")
  public Result<Void> cancelOrder(@Valid @RequestBody CancelOrderCommand command) {
    tradeApplicationService.cancelOrder(command);
    return Result.success(null);
  }

  @Operation(summary = "查询当日委托")
  @GetMapping("/orders/today")
  public Result<PageResult<OrderVO>> getTodayOrders(
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
    return Result.success(tradeApplicationService.getTodayOrders(page, size));
  }

  @Operation(summary = "查询历史委托")
  @GetMapping("/orders/history")
  public Result<PageResult<OrderVO>> getHistoryOrders(
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
    return Result.success(tradeApplicationService.getHistoryOrders(page, size));
  }

  @Operation(summary = "查询成交记录")
  @GetMapping("/trades")
  public Result<PageResult<TradeVO>> getTrades(
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
    return Result.success(tradeApplicationService.getTrades(page, size));
  }
}
