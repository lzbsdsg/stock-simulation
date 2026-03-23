package com.lzbsdsg.stocksimulation.market.infrastructure.adapter;

import com.lzbsdsg.stocksimulation.market.domain.entity.KLinePeriod;
import com.lzbsdsg.stocksimulation.market.domain.entity.KLinePoint;
import com.lzbsdsg.stocksimulation.market.domain.entity.QuoteSnapshot;
import com.lzbsdsg.stocksimulation.market.domain.gateway.MarketDataProvider;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 新浪财经行情适配器
 *
 * <p>调用新浪实时行情 HTTP 接口，解析返回数据并转换为领域对象。 优先级最高（@Order(1)）。
 */
@Slf4j
@Order(1)
@Component
public class SinaMarketDataAdapter implements MarketDataProvider {

  private static final String SINA_QUOTE_URL = "https://hq.sinajs.cn/list=";
  private static final DateTimeFormatter SINA_DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final HttpClient httpClient;

  public SinaMarketDataAdapter() {
    this(
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build());
  }

  SinaMarketDataAdapter(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  @Override
  public QuoteSnapshot getQuote(String stockCode) {
    String payload = fetchQuotePayload(List.of(normalizeStockCode(stockCode)));
    for (String line : payload.split(";")) {
      QuoteSnapshot parsed = parseQuoteLine(line, stockCode);
      if (parsed != null) {
        return parsed;
      }
    }
    throw new IllegalStateException("Sina quote payload has no valid line for " + stockCode);
  }

  @Override
  public List<KLinePoint> getKLine(
      String stockCode, KLinePeriod period, LocalDate from, LocalDate to) {
    QuoteSnapshot latest = getQuote(stockCode);
    return buildSyntheticKLine(period, from, to, latest);
  }

  @Override
  public List<QuoteSnapshot> batchGetQuotes(List<String> stockCodes) {
    List<String> normalizedCodes = stockCodes.stream().map(this::normalizeStockCode).distinct().toList();
    String payload = fetchQuotePayload(normalizedCodes);
    List<QuoteSnapshot> result = new ArrayList<>();
    for (String line : payload.split(";")) {
      QuoteSnapshot parsed = parseQuoteLine(line, null);
      if (parsed != null) {
        result.add(parsed);
      }
    }
    return result;
  }

  @Override
  public boolean isAvailable() {
    try {
      return getQuote("sh600519") != null;
    } catch (Exception ex) {
      log.warn("Sina provider health-check failed: {}", ex.getMessage());
      return false;
    }
  }

  QuoteSnapshot parseQuoteLine(String line, String fallbackStockCode) {
    if (line == null || line.isBlank() || !line.contains("hq_str_")) {
      return null;
    }
    int codeStart = line.indexOf("hq_str_") + "hq_str_".length();
    int codeEnd = line.indexOf('=');
    if (codeStart <= 0 || codeEnd <= codeStart) {
      return null;
    }

    String code = line.substring(codeStart, codeEnd).trim();
    String quoteData = extractQuotedData(line);
    if (quoteData.isBlank()) {
      return null;
    }

    String[] fields = quoteData.split(",");
    if (fields.length < 10) {
      return null;
    }

    QuoteSnapshot snapshot = new QuoteSnapshot();
    snapshot.setStockCode(normalizeStockCode(code.isBlank() ? fallbackStockCode : code));
    snapshot.setStockName(fields[0].trim());
    snapshot.setOpenPrice(parseDecimal(fields, 1));
    snapshot.setClosePrice(parseDecimal(fields, 2));
    snapshot.setCurrentPrice(parseDecimal(fields, 3));
    snapshot.setHighPrice(parseDecimal(fields, 4));
    snapshot.setLowPrice(parseDecimal(fields, 5));
    snapshot.setVolume(parseLong(fields, 8));
    snapshot.setAmount(parseDecimal(fields, 9));
    snapshot.setChangePercent(calcChangePercent(snapshot.getCurrentPrice(), snapshot.getClosePrice()));
    if (snapshot.getClosePrice() != null) {
      snapshot.setUpperLimitPrice(
          snapshot.getClosePrice().multiply(BigDecimal.valueOf(1.10)).setScale(2, RoundingMode.HALF_UP));
      snapshot.setLowerLimitPrice(
          snapshot.getClosePrice().multiply(BigDecimal.valueOf(0.90)).setScale(2, RoundingMode.HALF_UP));
    }
    snapshot.setTimestamp(parseTimestamp(fields));
    return snapshot;
  }

  private List<KLinePoint> buildSyntheticKLine(
      KLinePeriod period, LocalDate from, LocalDate to, QuoteSnapshot latest) {
    List<KLinePoint> points = new ArrayList<>();
    LocalDate cursor = from;
    BigDecimal base = latest.getClosePrice() != null ? latest.getClosePrice() : BigDecimal.ONE;
    while (!cursor.isAfter(to)) {
      KLinePoint point = new KLinePoint();
      point.setDate(cursor);
      point.setOpen(base.setScale(2, RoundingMode.HALF_UP));
      BigDecimal close = base.multiply(BigDecimal.valueOf(1.002)).setScale(2, RoundingMode.HALF_UP);
      point.setClose(close);
      point.setHigh(close.multiply(BigDecimal.valueOf(1.01)).setScale(2, RoundingMode.HALF_UP));
      point.setLow(close.multiply(BigDecimal.valueOf(0.99)).setScale(2, RoundingMode.HALF_UP));
      point.setVolume(latest.getVolume() == null ? 0L : latest.getVolume());
      point.setAmount(latest.getAmount() == null ? BigDecimal.ZERO : latest.getAmount());
      points.add(point);

      base = close;
      cursor = nextCursor(cursor, period);
    }
    return points;
  }

  private LocalDate nextCursor(LocalDate cursor, KLinePeriod period) {
    return switch (period) {
      case WEEKLY -> cursor.plusWeeks(1);
      case MONTHLY -> cursor.plusMonths(1);
      default -> cursor.plusDays(1);
    };
  }

  private String fetchQuotePayload(List<String> stockCodes) {
    try {
      String joinedCodes = String.join(",", stockCodes);
      String url = SINA_QUOTE_URL + URLEncoder.encode(joinedCodes, StandardCharsets.UTF_8);
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .timeout(Duration.ofSeconds(2))
              .header("Accept", "text/plain")
              .GET()
              .build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() >= 400) {
        throw new IllegalStateException("Sina response status is " + response.statusCode());
      }
      return response.body();
    } catch (IOException | InterruptedException ex) {
      if (ex instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new IllegalStateException("Failed to request Sina quote", ex);
    }
  }

  private String extractQuotedData(String line) {
    int start = line.indexOf('"');
    int end = line.lastIndexOf('"');
    if (start < 0 || end <= start) {
      return "";
    }
    return line.substring(start + 1, end);
  }

  private LocalDateTime parseTimestamp(String[] fields) {
    if (fields.length < 32) {
      return LocalDateTime.now();
    }
    String date = fields[30].trim();
    String time = fields[31].trim();
    if (date.isEmpty() || time.isEmpty()) {
      return LocalDateTime.now();
    }
    try {
      return LocalDateTime.parse(date + " " + time, SINA_DATE_TIME_FORMATTER);
    } catch (Exception ex) {
      return LocalDateTime.now();
    }
  }

  private BigDecimal calcChangePercent(BigDecimal current, BigDecimal close) {
    if (current == null || close == null || BigDecimal.ZERO.compareTo(close) == 0) {
      return BigDecimal.ZERO;
    }
    return current
        .subtract(close)
        .multiply(BigDecimal.valueOf(100))
        .divide(close, 2, RoundingMode.HALF_UP);
  }

  private BigDecimal parseDecimal(String[] fields, int index) {
    if (index < 0 || index >= fields.length) {
      return null;
    }
    String value = fields[index].trim();
    if (value.isEmpty()) {
      return null;
    }
    try {
      return new BigDecimal(value);
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private Long parseLong(String[] fields, int index) {
    if (index < 0 || index >= fields.length) {
      return 0L;
    }
    String value = fields[index].trim();
    if (value.isEmpty()) {
      return 0L;
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException ex) {
      return 0L;
    }
  }

  private String normalizeStockCode(String stockCode) {
    if (stockCode == null) {
      return "";
    }
    String code = stockCode.trim().toLowerCase(Locale.ROOT);
    if (code.startsWith("sh") || code.startsWith("sz")) {
      return code;
    }
    if (code.matches("^6\\d{5}$")) {
      return "sh" + code;
    }
    if (code.matches("^[03]\\d{5}$")) {
      return "sz" + code;
    }
    return code;
  }
}
