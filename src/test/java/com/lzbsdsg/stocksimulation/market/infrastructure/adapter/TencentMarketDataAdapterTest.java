package com.lzbsdsg.stocksimulation.market.infrastructure.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.lzbsdsg.stocksimulation.market.domain.entity.KLinePeriod;
import com.lzbsdsg.stocksimulation.market.domain.entity.KLinePoint;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TencentMarketDataAdapterTest {

  @Test
  @SuppressWarnings("unchecked")
  void should_get_daily_kline_from_tencent_history_endpoint() throws Exception {
    HttpClient httpClient = Mockito.mock(HttpClient.class);
    HttpResponse<String> response = Mockito.mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(200);
    when(response.body())
        .thenReturn(
            """
            {
              "code":0,
              "msg":"",
              "data":{
                "sh600519":{
                  "qfqday":[
                    ["2026-04-18","1660.00","1672.00","1680.00","1650.00","12345"],
                    ["2026-04-19","1672.00","1688.00","1695.00","1668.00","22345"]
                  ]
                }
              }
            }
            """);
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(response);

    TencentMarketDataAdapter adapter = new TencentMarketDataAdapter(httpClient);
    List<KLinePoint> points =
        adapter.getKLine(
            "sh600519",
            KLinePeriod.DAILY,
            LocalDate.parse("2026-04-18"),
            LocalDate.parse("2026-04-19"));

    assertEquals(2, points.size());
    assertEquals(LocalDate.parse("2026-04-18"), points.get(0).getDate());
    assertEquals("1660.00", points.get(0).getOpen().toPlainString());
    assertEquals(1234500L, points.get(0).getVolume());
    assertEquals("1688.00", points.get(1).getClose().toPlainString());
  }
}
