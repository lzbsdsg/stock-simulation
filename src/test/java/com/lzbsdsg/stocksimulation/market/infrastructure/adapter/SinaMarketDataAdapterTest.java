package com.lzbsdsg.stocksimulation.market.infrastructure.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.lzbsdsg.stocksimulation.market.domain.entity.QuoteSnapshot;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
    HttpResponse<String> response = Mockito.mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(200);
    when(response.body())
        .thenReturn(
            "var hq_str_sh600519=\"贵州茅台,1668.00,1650.00,1670.00,1680.00,1648.00,0,0,123456,123456789.00,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2026-03-24,10:01:02,00\";"
                + "var hq_str_sz000001=\"平安银行,11.10,11.00,11.23,11.30,10.98,0,0,999999,1234567.00,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2026-03-24,10:01:03,00\";");
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

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
}
