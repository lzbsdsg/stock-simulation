package com.lzbsdsg.stocksimulation.portfolio.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lzbsdsg.stocksimulation.market.domain.entity.QuoteSnapshot;
import com.lzbsdsg.stocksimulation.market.domain.service.MarketDataFacade;
import com.lzbsdsg.stocksimulation.portfolio.domain.entity.AssetSnapshot;
import com.lzbsdsg.stocksimulation.portfolio.domain.entity.Position;
import com.lzbsdsg.stocksimulation.portfolio.domain.repository.AssetSnapshotRepository;
import com.lzbsdsg.stocksimulation.portfolio.domain.repository.PositionRepository;
import com.lzbsdsg.stocksimulation.user.domain.entity.Account;
import com.lzbsdsg.stocksimulation.user.domain.repository.AccountRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssetSnapshotServiceTest {

  @Mock private AccountRepository accountRepository;
  @Mock private PositionRepository positionRepository;
  @Mock private AssetSnapshotRepository assetSnapshotRepository;
  @Mock private MarketDataFacade marketDataFacade;

  private AssetSnapshotService assetSnapshotService;

  @BeforeEach
  void setUp() {
    assetSnapshotService =
        new AssetSnapshotService(
            accountRepository, positionRepository, assetSnapshotRepository, marketDataFacade);
  }

  @Test
  void should_generate_daily_snapshot_correctly() {
    LocalDate snapshotDate = LocalDate.of(2026, 3, 25);
    Position position = position(1001L, "sh600519", 100, "10.0000");
    Account account = account(1001L, "100000.00", "90000.00", "10000.00");
    QuoteSnapshot quote = quote("sh600519", "11.00", "10.80");

    when(positionRepository.findByUserId(1001L)).thenReturn(List.of(position));
    when(marketDataFacade.batchGetQuotes(List.of("sh600519"))).thenReturn(List.of(quote));
    when(assetSnapshotRepository.findByUserIdAndDate(1001L, snapshotDate)).thenReturn(Optional.empty());
    when(accountRepository.findByUserId(1001L)).thenReturn(Optional.of(account));
    when(assetSnapshotRepository.findLatestBefore(1001L, snapshotDate))
        .thenReturn(Optional.of(previousSnapshot("99500.00")));

    AssetSnapshotService.SnapshotBatchResult result =
        assetSnapshotService.createDailySnapshots(snapshotDate, List.of(1001L));

    assertEquals(1, result.created());
    assertEquals(0, result.skipped());
    assertEquals(0, result.failed());

    ArgumentCaptor<AssetSnapshot> snapshotCaptor = ArgumentCaptor.forClass(AssetSnapshot.class);
    verify(assetSnapshotRepository).save(snapshotCaptor.capture());
    AssetSnapshot snapshot = snapshotCaptor.getValue();
    assertEquals(new BigDecimal("101080.00"), snapshot.getTotalAssets());
    assertEquals(new BigDecimal("1080.00"), snapshot.getMarketValue());
    assertEquals(new BigDecimal("1580.00"), snapshot.getDailyProfit());
    assertEquals(new BigDecimal("1.0800"), snapshot.getCumulativeProfitRate());
  }

  @Test
  void should_be_idempotent_for_same_date() {
    LocalDate snapshotDate = LocalDate.of(2026, 3, 25);
    Position position = position(1001L, "sh600519", 100, "10.0000");

    when(positionRepository.findByUserId(1001L)).thenReturn(List.of(position));
    when(marketDataFacade.batchGetQuotes(List.of("sh600519"))).thenReturn(List.of(quote("sh600519", "11.00", null)));
    when(assetSnapshotRepository.findByUserIdAndDate(1001L, snapshotDate))
        .thenReturn(Optional.of(new AssetSnapshot()));

    AssetSnapshotService.SnapshotBatchResult result =
        assetSnapshotService.createDailySnapshots(snapshotDate, List.of(1001L));

    assertEquals(0, result.created());
    assertEquals(1, result.skipped());
    assertEquals(0, result.failed());
    verify(assetSnapshotRepository, never()).save(any());
    verify(accountRepository, never()).findByUserId(any());
  }

  @Test
  void should_generate_snapshot_when_no_positions() {
    LocalDate snapshotDate = LocalDate.of(2026, 3, 25);
    Account account = account(1001L, "100000.00", "95000.00", "5000.00");

    when(positionRepository.findByUserId(1001L)).thenReturn(List.of());
    when(assetSnapshotRepository.findByUserIdAndDate(1001L, snapshotDate)).thenReturn(Optional.empty());
    when(accountRepository.findByUserId(1001L)).thenReturn(Optional.of(account));
    when(assetSnapshotRepository.findLatestBefore(1001L, snapshotDate)).thenReturn(Optional.empty());

    AssetSnapshotService.SnapshotBatchResult result =
        assetSnapshotService.createDailySnapshots(snapshotDate, List.of(1001L));

    assertEquals(1, result.created());
    ArgumentCaptor<AssetSnapshot> snapshotCaptor = ArgumentCaptor.forClass(AssetSnapshot.class);
    verify(assetSnapshotRepository).save(snapshotCaptor.capture());
    AssetSnapshot snapshot = snapshotCaptor.getValue();
    assertEquals(new BigDecimal("0.00"), snapshot.getMarketValue());
    assertEquals(new BigDecimal("100000.00"), snapshot.getTotalAssets());
    assertEquals(new BigDecimal("0.00"), snapshot.getDailyProfit());
    assertEquals(new BigDecimal("0.0000"), snapshot.getCumulativeProfitRate());
    verify(marketDataFacade, never()).batchGetQuotes(any());
  }

  private Position position(Long userId, String stockCode, int quantity, String costPrice) {
    Position position = new Position();
    position.setUserId(userId);
    position.setStockCode(stockCode);
    position.setTotalQuantity(quantity);
    position.setCostPrice(new BigDecimal(costPrice));
    return position;
  }

  private Account account(Long userId, String initial, String available, String frozen) {
    Account account = new Account();
    account.setUserId(userId);
    account.setInitialBalance(new BigDecimal(initial));
    account.setAvailableBalance(new BigDecimal(available));
    account.setFrozenBalance(new BigDecimal(frozen));
    return account;
  }

  private QuoteSnapshot quote(String stockCode, String currentPrice, String closePrice) {
    QuoteSnapshot quote = new QuoteSnapshot();
    quote.setStockCode(stockCode);
    quote.setCurrentPrice(new BigDecimal(currentPrice));
    if (closePrice != null) {
      quote.setClosePrice(new BigDecimal(closePrice));
    }
    return quote;
  }

  private AssetSnapshot previousSnapshot(String totalAssets) {
    AssetSnapshot snapshot = new AssetSnapshot();
    snapshot.setTotalAssets(new BigDecimal(totalAssets));
    return snapshot;
  }
}

