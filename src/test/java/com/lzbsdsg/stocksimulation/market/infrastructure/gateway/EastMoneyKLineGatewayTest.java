package com.lzbsdsg.stocksimulation.market.infrastructure.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lzbsdsg.stocksimulation.market.domain.entity.KLinePoint;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class EastMoneyKLineGatewayTest {

  @Test
  @SuppressWarnings("unchecked")
  void should_parse_daily_kline_payload() throws Exception {
    HttpClient httpClient = Mockito.mock(HttpClient.class);
    HttpResponse<String> response = Mockito.mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(200);
    when(response.body())
        .thenReturn(
            "{\"data\":{\"klines\":["
                + "\"2026-03-24,1668.00,1670.00,1680.00,1648.00,123456,123456789.00\","
                + "\"2026-03-25,1670.00,1682.00,1688.00,1665.00,113322,112233445.00\""
                + "]}}");
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

    EastMoneyKLineGateway gateway = new EastMoneyKLineGateway(httpClient, new ObjectMapper());
    List<KLinePoint> points =
        gateway.fetchDailyKLine(
            "sh600519", LocalDate.parse("2026-03-24"), LocalDate.parse("2026-03-25"));

    assertEquals(2, points.size());
    assertEquals(LocalDate.parse("2026-03-24"), points.get(0).getDate());
    assertEquals("1670.00", points.get(0).getClose().toPlainString());
    assertEquals(123456L, points.get(0).getVolume());
  }

  @Test
  void should_return_empty_when_code_invalid() {
    HttpClient httpClient = Mockito.mock(HttpClient.class);
    EastMoneyKLineGateway gateway = new EastMoneyKLineGateway(httpClient, new ObjectMapper());

    List<KLinePoint> points =
        gateway.fetchDailyKLine(
            "BADCODE", LocalDate.parse("2026-03-24"), LocalDate.parse("2026-03-25"));

    assertTrue(points.isEmpty());
    verifyNoInteractions(httpClient);
  }
}
