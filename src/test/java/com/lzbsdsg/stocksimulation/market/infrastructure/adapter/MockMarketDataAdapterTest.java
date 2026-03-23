package com.lzbsdsg.stocksimulation.market.infrastructure.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.lzbsdsg.stocksimulation.market.domain.entity.KLinePeriod;
import com.lzbsdsg.stocksimulation.market.domain.entity.KLinePoint;
import com.lzbsdsg.stocksimulation.market.domain.entity.QuoteSnapshot;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class MockMarketDataAdapterTest {

  private final MockMarketDataAdapter adapter = new MockMarketDataAdapter();

  @Test
  void should_generate_quote_with_required_fields() {
    QuoteSnapshot quote = adapter.getQuote("sh600519");

    assertNotNull(quote);
    assertEquals("sh600519", quote.getStockCode());
    assertNotNull(quote.getCurrentPrice());
    assertNotNull(quote.getTimestamp());
    assertNotNull(quote.getVolume());
  }

  @Test
  void should_generate_kline_points_in_date_range() {
    LocalDate from = LocalDate.parse("2026-03-01");
    LocalDate to = LocalDate.parse("2026-03-05");

    List<KLinePoint> points = adapter.getKLine("sh600519", KLinePeriod.DAILY, from, to);

    assertFalse(points.isEmpty());
    assertEquals(from, points.get(0).getDate());
    assertEquals(to, points.get(points.size() - 1).getDate());
  }
}
