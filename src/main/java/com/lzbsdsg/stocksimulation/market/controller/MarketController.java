package com.lzbsdsg.stocksimulation.market.controller;

import com.lzbsdsg.stocksimulation.common.result.Result;
import com.lzbsdsg.stocksimulation.market.application.MarketApplicationService;
import com.lzbsdsg.stocksimulation.market.application.vo.KLineVO;
import com.lzbsdsg.stocksimulation.market.application.vo.QuoteVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

/** 行情控制器 */
@Tag(name = "行情接口")
@RestController
@RequestMapping("/api/v1/market")
@RequiredArgsConstructor
public class MarketController {

  private final MarketApplicationService marketApplicationService;

  @Operation(summary = "获取单只股票实时行情")
  @GetMapping("/quote/{stockCode}")
  public Result<QuoteVO> getQuote(@PathVariable String stockCode) {
    return Result.success(marketApplicationService.getQuote(stockCode));
  }

  @Operation(summary = "批量获取股票行情")
  @GetMapping("/quotes")
  public Result<List<QuoteVO>> batchGetQuotes(@RequestParam List<String> stockCodes) {
    return Result.success(marketApplicationService.batchGetQuotes(stockCodes));
  }

  @Operation(summary = "获取K线数据")
  @GetMapping("/kline/{stockCode}")
  public Result<List<KLineVO>> getKLine(
      @PathVariable String stockCode,
      @RequestParam(defaultValue = "DAILY") String period,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return Result.success(marketApplicationService.getKLine(stockCode, period, from, to));
  }

  @Operation(summary = "搜索股票")
  @GetMapping("/search")
  public Result<List<QuoteVO>> searchStock(@RequestParam String keyword) {
    return Result.success(marketApplicationService.searchStock(keyword));
  }
}
