package com.lzbsdsg.stocksimulation.market.infrastructure.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.lzbsdsg.stocksimulation.market.domain.entity.KLinePeriod;
import com.lzbsdsg.stocksimulation.market.domain.entity.KLinePoint;
import com.lzbsdsg.stocksimulation.market.domain.entity.QuoteSnapshot;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** 新浪行情适配器单元测试。 覆盖：正常解析、异常响应、超时降级。 */
class SinaMarketDataAdapterTest {

  @Test
  void should_parse_sina_quote_response() {
    SinaMarketDataAdapter adapter = new SinaMarketDataAdapter(HttpClient.newHttpClient());
    String line =
        "var hq_str_sh600519=\"贵州茅台,1668.00,1650.00,1670.00,1680.00,1648.00,0,0,123456,123456789.00,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2026-03-24,10:01:02,00\";";

    QuoteSnapshot snapshot = adapter.parseQuoteLine(line, "sh600519");

    assertNotNull(snapshot);
    assertEquals("sh600519", snapshot.getStockCode());
    assertEquals("贵州茅台", snapshot.getStockName());
    assertEquals("1670.00", snapshot.getCurrentPrice().toPlainString());
    assertEquals(123456L, snapshot.getVolume());
  }

  @Test
  void should_return_null_on_invalid_response() {
    SinaMarketDataAdapter adapter = new SinaMarketDataAdapter(HttpClient.newHttpClient());

    QuoteSnapshot snapshot = adapter.parseQuoteLine("invalid", "sh600519");

    assertNull(snapshot);
  }

  @Test
  @SuppressWarnings("unchecked")
  void should_batch_get_quotes() throws Exception {
    HttpClient httpClient = Mockito.mock(HttpClient.class);
    HttpResponse<byte[]> response = Mockito.mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(200);
    String payload =
        "var hq_str_sh600519=\"贵州茅台,1668.00,1650.00,1670.00,1680.00,1648.00,0,0,123456,123456789.00,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2026-03-24,10:01:02,00\";"
            + "var hq_str_sz000001=\"平安银行,11.10,11.00,11.23,11.30,10.98,0,0,999999,1234567.00,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2026-03-24,10:01:03,00\";";
    when(response.body()).thenReturn(payload.getBytes(StandardCharsets.UTF_8));
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(response);

    SinaMarketDataAdapter adapter = new SinaMarketDataAdapter(httpClient);
    List<QuoteSnapshot> quotes = adapter.batchGetQuotes(List.of("sh600519", "sz000001"));

    assertEquals(2, quotes.size());
  }

  @Test
  @SuppressWarnings("unchecked")
  void should_handle_timeout_gracefully() throws Exception {
    HttpClient httpClient = Mockito.mock(HttpClient.class);
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenThrow(new IOException("timeout"));
    SinaMarketDataAdapter adapter = new SinaMarketDataAdapter(httpClient);

    assertFalse(adapter.isAvailable());
  }

  @Test
  @SuppressWarnings("unchecked")
  void should_get_daily_kline_from_sina_history_endpoint() throws Exception {
    HttpClient httpClient = Mockito.mock(HttpClient.class);
    HttpResponse<String> response = Mockito.mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(200);
    when(response.body())
        .thenReturn(
            """
            [
              {"day":"2026-04-18","open":"1660.00","high":"1680.00","low":"1650.00","close":"1672.00","volume":"1234567"},
              {"day":"2026-04-19","open":"1672.00","high":"1695.00","low":"1668.00","close":"1688.00","volume":"2234567"}
            ]
            """);
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(response);

    SinaMarketDataAdapter adapter = new SinaMarketDataAdapter(httpClient);
    List<KLinePoint> points =
        adapter.getKLine(
            "sh600519",
            KLinePeriod.DAILY,
            LocalDate.parse("2026-04-18"),
            LocalDate.parse("2026-04-19"));

    assertEquals(2, points.size());
    assertEquals(LocalDate.parse("2026-04-18"), points.get(0).getDate());
    assertEquals("1660.00", points.get(0).getOpen().toPlainString());
    assertEquals("1688.00", points.get(1).getClose().toPlainString());
  }
}
