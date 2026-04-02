package com.lzbsdsg.stocksimulation.watchlist.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lzbsdsg.stocksimulation.common.exception.BizException;
import com.lzbsdsg.stocksimulation.common.result.ErrorCode;
import com.lzbsdsg.stocksimulation.market.domain.entity.QuoteSnapshot;
import com.lzbsdsg.stocksimulation.market.domain.service.MarketDataFacade;
import com.lzbsdsg.stocksimulation.watchlist.application.vo.WatchlistItemVO;
import com.lzbsdsg.stocksimulation.watchlist.domain.entity.WatchlistItem;
import com.lzbsdsg.stocksimulation.watchlist.domain.repository.WatchlistRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class WatchlistApplicationServiceTest {

    @Mock
    private WatchlistRepository watchlistRepository;
    @Mock
    private MarketDataFacade marketDataFacade;

    @InjectMocks
    private WatchlistApplicationService watchlistApplicationService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("1001", null));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void should_get_watchlist_with_realtime_quote() {
        WatchlistItem item = new WatchlistItem();
        item.setUserId(1001L);
        item.setStockCode("sh600519");
        item.setStockName("贵州茅台");
        item.setSortOrder(1);

        QuoteSnapshot quote = new QuoteSnapshot();
        quote.setStockCode("sh600519");
        quote.setStockName("贵州茅台");
        quote.setCurrentPrice(new BigDecimal("1888.88"));
        quote.setChangePercent(new BigDecimal("1.2300"));

        when(watchlistRepository.findByUserId(1001L)).thenReturn(List.of(item));
        when(marketDataFacade.batchGetQuotes(List.of("sh600519"))).thenReturn(List.of(quote));

        List<WatchlistItemVO> result = watchlistApplicationService.getWatchlist();

        assertEquals(1, result.size());
        assertEquals("sh600519", result.get(0).stockCode());
        assertEquals(new BigDecimal("1888.88"), result.get(0).currentPrice());
        assertEquals(new BigDecimal("1.2300"), result.get(0).changePercent());
    }

    @Test
    void should_reject_add_stock_when_already_exists() {
        WatchlistItem exists = new WatchlistItem();
        exists.setStockCode("sh600519");

        when(watchlistRepository.findByUserIdAndStockCode(1001L, "sh600519"))
                .thenReturn(Optional.of(exists));

        BizException ex = assertThrows(BizException.class, () -> watchlistApplicationService.addStock("SH600519"));

        assertEquals(ErrorCode.WATCHLIST_ALREADY_EXISTS, ex.getErrorCode());
        verify(watchlistRepository, never()).save(any());
    }

    @Test
    void should_reject_add_stock_when_limit_exceeded() {
        when(watchlistRepository.findByUserIdAndStockCode(1001L, "sh600519")).thenReturn(Optional.empty());
        when(watchlistRepository.countByUserId(1001L)).thenReturn(50L);

        BizException ex = assertThrows(BizException.class, () -> watchlistApplicationService.addStock("sh600519"));

        assertEquals(ErrorCode.WATCHLIST_LIMIT_EXCEEDED, ex.getErrorCode());
        verify(watchlistRepository, never()).save(any());
    }

    @Test
    void should_add_stock_successfully() {
        QuoteSnapshot quote = new QuoteSnapshot();
        quote.setStockCode("sh600519");
        quote.setStockName("贵州茅台");

        when(watchlistRepository.findByUserIdAndStockCode(1001L, "sh600519")).thenReturn(Optional.empty());
        when(watchlistRepository.countByUserId(1001L)).thenReturn(3L);
        when(marketDataFacade.getQuote("sh600519")).thenReturn(quote);

        watchlistApplicationService.addStock("SH600519");

        ArgumentCaptor<WatchlistItem> captor = ArgumentCaptor.forClass(WatchlistItem.class);
        verify(watchlistRepository).save(captor.capture());

        WatchlistItem saved = captor.getValue();
        assertEquals(1001L, saved.getUserId());
        assertEquals("sh600519", saved.getStockCode());
        assertEquals("贵州茅台", saved.getStockName());
        assertEquals(4, saved.getSortOrder());
    }

    @Test
    void should_reject_update_sort_when_codes_mismatch() {
        WatchlistItem item = new WatchlistItem();
        item.setId(1L);
        item.setUserId(1001L);
        item.setStockCode("sh600519");
        item.setSortOrder(1);

        when(watchlistRepository.findByUserId(1001L)).thenReturn(List.of(item));

        BizException ex = assertThrows(
                BizException.class,
                () -> watchlistApplicationService.updateSort(List.of("sh600519", "sz000001")));

        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        verify(watchlistRepository, never()).batchUpdateSort(any(), any());
    }

    @Test
    void should_update_sort_successfully() {
        WatchlistItem first = new WatchlistItem();
        first.setId(1L);
        first.setUserId(1001L);
        first.setStockCode("sh600519");
        first.setSortOrder(1);

        WatchlistItem second = new WatchlistItem();
        second.setId(2L);
        second.setUserId(1001L);
        second.setStockCode("sz000001");
        second.setSortOrder(2);

        when(watchlistRepository.findByUserId(1001L)).thenReturn(List.of(first, second));

        watchlistApplicationService.updateSort(List.of("sz000001", "sh600519"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<WatchlistItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(watchlistRepository).batchUpdateSort(org.mockito.ArgumentMatchers.eq(1001L), captor.capture());

        List<WatchlistItem> sorted = captor.getValue();
        assertEquals("sz000001", sorted.get(0).getStockCode());
        assertEquals(1, sorted.get(0).getSortOrder());
        assertEquals("sh600519", sorted.get(1).getStockCode());
        assertEquals(2, sorted.get(1).getSortOrder());
    }
}
