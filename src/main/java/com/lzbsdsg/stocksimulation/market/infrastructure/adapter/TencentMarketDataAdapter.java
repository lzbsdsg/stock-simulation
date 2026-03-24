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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 腾讯财经行情适配器（备用 Provider） */
@Slf4j
@Order(2)
@Component
public class TencentMarketDataAdapter implements MarketDataProvider {

  private static final String TENCENT_QUOTE_URL = "https://qt.gtimg.cn/q=";
  private static final Charset GBK = Charset.forName("GBK");
  private final HttpClient httpClient;

  public TencentMarketDataAdapter() {
    this(
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build());
  }

  TencentMarketDataAdapter(HttpClient httpClient) {
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
    throw new IllegalStateException("Tencent quote payload has no valid line for " + stockCode);
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
      log.warn("Tencent provider health-check failed: {}", ex.getMessage());
      return false;
    }
  }

  QuoteSnapshot parseQuoteLine(String line, String fallbackStockCode) {
    if (line == null || line.isBlank() || !line.contains("~")) {
      return null;
    }

    int codeStart = line.indexOf("v_");
    int codeEnd = line.indexOf('=');
    String code = fallbackStockCode;
    if (codeStart >= 0 && codeEnd > codeStart) {
      code = line.substring(codeStart + 2, codeEnd).trim();
    }

    String body = extractQuotedData(line);
    if (body.isBlank()) {
      return null;
    }
    String[] fields = body.split("~");
    if (fields.length < 6) {
      return null;
    }

    BigDecimal current = firstDecimal(fields, 3);
    BigDecimal close = firstDecimal(fields, 4);
    BigDecimal open = firstDecimal(fields, 5);
    BigDecimal high = firstDecimal(fields, 33, 41);
    BigDecimal low = firstDecimal(fields, 34, 42);
    Long volume = firstLong(fields, 6, 36, 37);
    BigDecimal amount = firstDecimal(fields, 37, 38);

    QuoteSnapshot snapshot = new QuoteSnapshot();
    snapshot.setStockCode(normalizeStockCode(code));
    snapshot.setStockName(fieldOrBlank(fields, 1));
    snapshot.setCurrentPrice(current);
    snapshot.setClosePrice(close);
    snapshot.setOpenPrice(open);
    snapshot.setHighPrice(high != null ? high : current);
    snapshot.setLowPrice(low != null ? low : current);
    snapshot.setVolume(volume != null ? volume : 0L);
    snapshot.setAmount(amount != null ? amount : BigDecimal.ZERO);
    snapshot.setChangePercent(calcChangePercent(current, close));
    if (close != null) {
      snapshot.setUpperLimitPrice(close.multiply(BigDecimal.valueOf(1.10)).setScale(2, RoundingMode.HALF_UP));
      snapshot.setLowerLimitPrice(close.multiply(BigDecimal.valueOf(0.90)).setScale(2, RoundingMode.HALF_UP));
    }
    snapshot.setTimestamp(LocalDateTime.now());
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
      BigDecimal close = base.multiply(BigDecimal.valueOf(0.998)).setScale(2, RoundingMode.HALF_UP);
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
      String url = TENCENT_QUOTE_URL + URLEncoder.encode(joinedCodes, StandardCharsets.UTF_8);
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .timeout(Duration.ofSeconds(2))
              .header("Accept", "text/plain")
            .header(
              "User-Agent",
              "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .header("Referer", "https://gu.qq.com")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
              .GET()
              .build();
      HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
      if (response.statusCode() >= 400) {
        throw new IllegalStateException("Tencent response status is " + response.statusCode());
      }
      return decodePayload(response);
    } catch (IOException | InterruptedException ex) {
      if (ex instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new IllegalStateException("Failed to request Tencent quote", ex);
    }
  }

  private String decodePayload(HttpResponse<byte[]> response) {
    byte[] body = response.body();
    if (body == null || body.length == 0) {
      return "";
    }

    Charset charset = resolveCharset(response);
    if (charset != null) {
      return new String(body, charset);
    }

    String utf8Body = new String(body, StandardCharsets.UTF_8);
    // Some quote APIs return GBK without charset header; fallback on obvious mojibake marker.
    if (utf8Body.indexOf('\uFFFD') >= 0) {
      return new String(body, GBK);
    }
    return utf8Body;
  }

  private Charset resolveCharset(HttpResponse<byte[]> response) {
    Optional<String> contentTypeValue =
        response.headers() == null ? Optional.empty() : response.headers().firstValue("Content-Type");
    String contentType = contentTypeValue.orElse("");
    if (contentType.isBlank()) {
      return null;
    }
    int idx = contentType.toLowerCase(Locale.ROOT).indexOf("charset=");
    if (idx < 0) {
      return null;
    }
    String name = contentType.substring(idx + "charset=".length()).trim();
    int semicolon = name.indexOf(';');
    if (semicolon >= 0) {
      name = name.substring(0, semicolon).trim();
    }
    try {
      return Charset.forName(name);
    } catch (Exception ex) {
      return null;
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

  private String fieldOrBlank(String[] fields, int index) {
    if (index < 0 || index >= fields.length) {
      return "";
    }
    return fields[index] == null ? "" : fields[index].trim();
  }

  private BigDecimal firstDecimal(String[] fields, int... indexes) {
    for (int index : indexes) {
      BigDecimal value = parseDecimal(fields, index);
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  private Long firstLong(String[] fields, int... indexes) {
    for (int index : indexes) {
      Long value = parseLong(fields, index);
      if (value != null) {
        return value;
      }
    }
    return null;
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
      return null;
    }
    String value = fields[index].trim();
    if (value.isEmpty()) {
      return null;
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException ex) {
      return null;
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
