package com.lzbsdsg.stocksimulation.market.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lzbsdsg.stocksimulation.market.domain.entity.KLinePeriod;
import com.lzbsdsg.stocksimulation.market.domain.entity.KLinePoint;
import com.lzbsdsg.stocksimulation.market.domain.entity.QuoteSnapshot;
import com.lzbsdsg.stocksimulation.market.domain.gateway.MarketDataProvider;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * AkShare 行情适配器。
 *
 * <p>注意：Java 进程通过 HTTP 调用本地 AkShare 网关服务（Python + AkShare）。
 */
@Slf4j
@Order(0)
@Component
@ConditionalOnProperty(prefix = "market.provider.akshare", name = "enabled", havingValue = "true")
public class AkshareMarketDataAdapter implements MarketDataProvider {

  private final String baseUrl;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  @Autowired
  public AkshareMarketDataAdapter(
      @Value("${market.provider.akshare.base-url:http://127.0.0.1:8000}") String baseUrl) {
    this(
        baseUrl,
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build(),
        new ObjectMapper());
  }

  AkshareMarketDataAdapter(String baseUrl, HttpClient httpClient, ObjectMapper objectMapper) {
    this.baseUrl = trimEndSlash(baseUrl);
    this.httpClient = httpClient;
    this.objectMapper = objectMapper;
  }

  @Override
  public QuoteSnapshot getQuote(String stockCode) {
    String normalized = normalizeStockCode(stockCode);
    String url = baseUrl + "/api/quote?symbol=" + encode(normalized);
    JsonNode json = requestJson(url);
    return toQuoteSnapshot(json);
  }

  @Override
  public List<KLinePoint> getKLine(
      String stockCode, KLinePeriod period, LocalDate from, LocalDate to) {
    String normalized = normalizeStockCode(stockCode);
    String periodValue = mapPeriod(period);
    String url =
        baseUrl
            + "/api/kline?symbol="
            + encode(normalized)
            + "&period="
            + encode(periodValue)
            + "&from="
            + encode(from.toString())
            + "&to="
            + encode(to.toString());
    JsonNode json = requestJson(url);

    List<KLinePoint> result = new ArrayList<>();
    if (!json.isArray()) {
      return result;
    }
    for (JsonNode node : json) {
      KLinePoint point = new KLinePoint();
      point.setDate(LocalDate.parse(node.path("date").asText()));
      point.setOpen(decimal(node.path("open").asText(null)));
      point.setClose(decimal(node.path("close").asText(null)));
      point.setHigh(decimal(node.path("high").asText(null)));
      point.setLow(decimal(node.path("low").asText(null)));
      point.setVolume(longValue(node.path("volume").asText(null)));
      point.setAmount(decimal(node.path("amount").asText(null)));
      result.add(point);
    }
    return result;
  }

  @Override
  public List<QuoteSnapshot> batchGetQuotes(List<String> stockCodes) {
    if (stockCodes == null || stockCodes.isEmpty()) {
      return List.of();
    }
    List<String> normalizedCodes =
        stockCodes.stream()
            .map(this::normalizeStockCode)
            .filter(code -> !code.isBlank())
            .distinct()
            .toList();
    String joined = String.join(",", normalizedCodes);
    String url = baseUrl + "/api/quotes?symbols=" + encode(joined);
    JsonNode json = requestJson(url);

    List<QuoteSnapshot> result = new ArrayList<>();
    if (!json.isArray()) {
      return result;
    }
    for (JsonNode node : json) {
      result.add(toQuoteSnapshot(node));
    }
    return result;
  }

  @Override
  public boolean isAvailable() {
    try {
      String url = baseUrl + "/health";
      JsonNode json = requestJson(url);
      return "UP".equalsIgnoreCase(json.path("status").asText(""));
    } catch (Exception ex) {
      log.warn("AkShare provider health-check failed: {}", ex.getMessage());
      return false;
    }
  }

  private JsonNode requestJson(String url) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .timeout(Duration.ofSeconds(3))
              .header("Accept", "application/json")
              .GET()
              .build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() >= 400) {
        throw new IllegalStateException(
            "AkShare gateway response status is " + response.statusCode());
      }
      return objectMapper.readTree(response.body());
    } catch (IOException | InterruptedException ex) {
      if (ex instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new IllegalStateException("Failed to request AkShare gateway", ex);
    }
  }

  private QuoteSnapshot toQuoteSnapshot(JsonNode node) {
    QuoteSnapshot snapshot = new QuoteSnapshot();
    snapshot.setStockCode(normalizeStockCode(node.path("stockCode").asText("")));
    snapshot.setStockName(node.path("stockName").asText(""));
    snapshot.setCurrentPrice(decimal(node.path("currentPrice").asText(null)));
    snapshot.setOpenPrice(decimal(node.path("openPrice").asText(null)));
    snapshot.setClosePrice(decimal(node.path("closePrice").asText(null)));
    snapshot.setHighPrice(decimal(node.path("highPrice").asText(null)));
    snapshot.setLowPrice(decimal(node.path("lowPrice").asText(null)));
    snapshot.setVolume(longValue(node.path("volume").asText(null)));
    snapshot.setAmount(decimal(node.path("amount").asText(null)));
    snapshot.setChangePercent(decimal(node.path("changePercent").asText(null)));
    String timestamp = node.path("timestamp").asText("");
    if (!timestamp.isBlank()) {
      snapshot.setTimestamp(LocalDateTime.parse(timestamp));
    } else {
      snapshot.setTimestamp(LocalDateTime.now());
    }
    return snapshot;
  }

  private String normalizeStockCode(String stockCode) {
    return stockCode == null ? "" : stockCode.trim().toLowerCase();
  }

  private String mapPeriod(KLinePeriod period) {
    return switch (period) {
      case WEEKLY -> "weekly";
      case MONTHLY -> "monthly";
      default -> "daily";
    };
  }

  private String trimEndSlash(String value) {
    if (value == null) {
      return "http://127.0.0.1:8000";
    }
    String trimmed = value.trim();
    if (trimmed.endsWith("/")) {
      return trimmed.substring(0, trimmed.length() - 1);
    }
    return trimmed;
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private BigDecimal decimal(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return new BigDecimal(value);
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private Long longValue(String value) {
    if (value == null || value.isBlank()) {
      return 0L;
    }
    try {
      return new BigDecimal(value).longValue();
    } catch (NumberFormatException ex) {
      return 0L;
    }
  }
}
