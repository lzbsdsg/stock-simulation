package com.lzbsdsg.stocksimulation.watchlist.controller;

import com.lzbsdsg.stocksimulation.common.result.Result;
import com.lzbsdsg.stocksimulation.watchlist.application.WatchlistApplicationService;
import com.lzbsdsg.stocksimulation.watchlist.application.vo.WatchlistItemVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 自选股控制器 */
@Tag(name = "自选股接口")
@RestController
@RequestMapping("/api/v1/watchlist")
@RequiredArgsConstructor
public class WatchlistController {

  private final WatchlistApplicationService watchlistApplicationService;

  @Operation(summary = "获取自选股列表")
  @GetMapping
  public Result<List<WatchlistItemVO>> getWatchlist() {
    return Result.success(watchlistApplicationService.getWatchlist());
  }

  @Operation(summary = "添加自选股")
  @PostMapping("/{stockCode}")
  public Result<Void> addStock(@PathVariable String stockCode) {
    watchlistApplicationService.addStock(stockCode);
    return Result.success(null);
  }

  @Operation(summary = "移除自选股")
  @DeleteMapping("/{stockCode}")
  public Result<Void> removeStock(@PathVariable String stockCode) {
    watchlistApplicationService.removeStock(stockCode);
    return Result.success(null);
  }

  @Operation(summary = "调整自选股排序")
  @PutMapping("/sort")
  public Result<Void> updateSort(@RequestBody List<String> stockCodes) {
    watchlistApplicationService.updateSort(stockCodes);
    return Result.success(null);
  }
}
