package com.lzbsdsg.stocksimulation.market.infrastructure.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lzbsdsg.stocksimulation.market.domain.entity.KLinePoint;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 东方财富历史日K网关 */
@Slf4j
@Component
public class EastMoneyKLineGateway {

  private static final String EAST_MONEY_KLINE_URL =
      "https://push2his.eastmoney.com/api/qt/stock/kline/get";
  private static final String USER_AGENT =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)"
          + " Chrome/124.0.0.0 Safari/537.36";
  private static final DateTimeFormatter COMPACT_DATE = DateTimeFormatter.BASIC_ISO_DATE;

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  @Value("${market.kline.fqt:1}")
  private String kLineAdjustMode = "1";

  public EastMoneyKLineGateway() {
    this(
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build(),
        new ObjectMapper());
  }

  EastMoneyKLineGateway(HttpClient httpClient, ObjectMapper objectMapper) {
    this.httpClient = httpClient;
    this.objectMapper = objectMapper;
  }

  public List<KLinePoint> fetchDailyKLine(String stockCode, LocalDate from, LocalDate to) {
    if (from == null || to == null || from.isAfter(to)) {
      return List.of();
    }

    String normalizedCode = normalizeStockCode(stockCode);
    String secId = toSecId(normalizedCode);
    if (secId.isBlank()) {
      return List.of();
    }

    String url = buildUrl(secId, from, to);
    JsonNode root = requestJson(url);
    JsonNode klinesNode = root.path("data").path("klines");
    if (!klinesNode.isArray()) {
      return List.of();
    }

    List<KLinePoint> points = new ArrayList<>();
    for (JsonNode node : klinesNode) {
      if (!node.isTextual()) {
        continue;
      }
      KLinePoint point = parseKLineLine(node.asText());
      if (point != null) {
        points.add(point);
      }
    }
    return points;
  }

  private String buildUrl(String secId, LocalDate from, LocalDate to) {
    return EAST_MONEY_KLINE_URL
        + "?secid="
        + encode(secId)
        + "&ut="
        + encode("fa5fd1943c7b386f172d6893dbfba10b")
        + "&fields1="
        + encode("f1,f2,f3,f4,f5,f6")
        + "&fields2="
        + encode("f51,f52,f53,f54,f55,f56,f57")
        + "&klt=101&fqt="
        + encode(kLineAdjustMode)
        + "&beg="
        + encode(from.format(COMPACT_DATE))
        + "&end="
        + encode(to.format(COMPACT_DATE));
  }

  private JsonNode requestJson(String url) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .timeout(Duration.ofSeconds(4))
              .header("Accept", "application/json,text/plain,*/*")
              .header("User-Agent", USER_AGENT)
              .header("Referer", "https://quote.eastmoney.com/")
              .GET()
              .build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() >= 400) {
        throw new IllegalStateException(
            "EastMoney kline response status is " + response.statusCode());
      }
      String body = extractJsonPayload(response.body());
      return objectMapper.readTree(body);
    } catch (IOException | InterruptedException ex) {
      if (ex instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new IllegalStateException("Failed to request EastMoney kline", ex);
    }
  }

  private String extractJsonPayload(String body) {
    if (body == null) {
      return "{}";
    }
    String trimmed = body.trim();
    if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
      return trimmed;
    }
    int start = trimmed.indexOf('{');
    int end = trimmed.lastIndexOf('}');
    if (start >= 0 && end > start) {
      return trimmed.substring(start, end + 1);
    }
    return "{}";
  }

  private KLinePoint parseKLineLine(String line) {
    String[] fields = line.split(",");
    if (fields.length < 7) {
      return null;
    }

    LocalDate date = parseLocalDate(fields[0]);
    BigDecimal open = parseDecimal(fields[1]);
    BigDecimal close = parseDecimal(fields[2]);
    BigDecimal high = parseDecimal(fields[3]);
    BigDecimal low = parseDecimal(fields[4]);
    Long volume = parseLong(fields[5]);
    BigDecimal amount = parseDecimal(fields[6]);
    if (date == null || open == null || close == null || high == null || low == null) {
      return null;
    }

    KLinePoint point = new KLinePoint();
    point.setDate(date);
    point.setOpen(open);
    point.setClose(close);
    point.setHigh(high);
    point.setLow(low);
    point.setVolume(volume == null ? 0L : volume);
    point.setAmount(amount == null ? BigDecimal.ZERO : amount);
    return point;
  }

  private LocalDate parseLocalDate(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(value.trim());
    } catch (Exception ex) {
      return null;
    }
  }

  private BigDecimal parseDecimal(String value) {
    if (value == null || value.isBlank() || "-".equals(value.trim())) {
      return null;
    }
    try {
      return new BigDecimal(value.trim());
    } catch (Exception ex) {
      return null;
    }
  }

  private Long parseLong(String value) {
    if (value == null || value.isBlank() || "-".equals(value.trim())) {
      return null;
    }
    try {
      return new BigDecimal(value.trim()).longValue();
    } catch (Exception ex) {
      return null;
    }
  }

  private String normalizeStockCode(String stockCode) {
    if (stockCode == null) {
      return "";
    }
    String code = stockCode.trim().toLowerCase(Locale.ROOT);
    if (code.startsWith("sh") || code.startsWith("sz") || code.startsWith("bj")) {
      return code;
    }
    if (code.matches("^6\\d{5}$")) {
      return "sh" + code;
    }
    if (code.matches("^[03]\\d{5}$")) {
      return "sz" + code;
    }
    if (code.matches("^8\\d{5}$") || code.matches("^4\\d{5}$")) {
      return "bj" + code;
    }
    return code;
  }

  private String toSecId(String normalizedCode) {
    if (normalizedCode == null || normalizedCode.length() < 8) {
      return "";
    }
    String exchange = normalizedCode.substring(0, 2);
    String code = normalizedCode.substring(2);
    if (!code.matches("\\d{6}")) {
      return "";
    }
    if ("sh".equals(exchange)) {
      return "1." + code;
    }
    // 深市/北交所均按 0. 处理。
    return "0." + code;
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
