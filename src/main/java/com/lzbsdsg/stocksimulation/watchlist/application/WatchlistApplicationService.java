package com.lzbsdsg.stocksimulation.watchlist.application;

import com.lzbsdsg.stocksimulation.watchlist.application.vo.WatchlistItemVO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 自选股应用服务 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WatchlistApplicationService {

  // TODO: 注入 WatchlistRepository, MarketDataFacade

  public List<WatchlistItemVO> getWatchlist() {
    // TODO: 查询用户自选股列表 + 实时行情
    throw new UnsupportedOperationException("getWatchlist not implemented");
  }

  public void addStock(String stockCode) {
    // TODO: 添加自选股（去重校验，最多50只）
    throw new UnsupportedOperationException("addStock not implemented");
  }

  public void removeStock(String stockCode) {
    // TODO: 移除自选股
    throw new UnsupportedOperationException("removeStock not implemented");
  }

  public void updateSort(List<String> stockCodes) {
    // TODO: 批量更新排序
    throw new UnsupportedOperationException("updateSort not implemented");
  }
}
