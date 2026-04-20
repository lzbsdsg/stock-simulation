package com.lzbsdsg.stocksimulation.market.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 腾讯财经行情适配器（备用 Provider） */
@Slf4j
@Order(2)
@Component
public class TencentMarketDataAdapter implements MarketDataProvider {

  private static final String TENCENT_QUOTE_URL = "https://qt.gtimg.cn/q=";
  private static final String TENCENT_KLINE_URL = "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?param=";
  private static final Charset GBK = Charset.forName("GBK");
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  public TencentMarketDataAdapter() {
    this(
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build(),
        new ObjectMapper());
  }

  TencentMarketDataAdapter(HttpClient httpClient) {
    this(httpClient, new ObjectMapper());
  }

  TencentMarketDataAdapter(HttpClient httpClient, ObjectMapper objectMapper) {
    this.httpClient = httpClient;
    this.objectMapper = objectMapper;
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
    if (from == null || to == null || from.isAfter(to)) {
      return List.of();
    }

    String normalizedCode = normalizeStockCode(stockCode);
    List<KLinePoint> dailyPoints = fetchDailyKLine(normalizedCode, from, to);
    if (period == KLinePeriod.DAILY) {
      return dailyPoints;
    }
    return aggregate(dailyPoints, period);
  }

  private List<KLinePoint> fetchDailyKLine(String stockCode, LocalDate from, LocalDate to) {
    long requestedDays = ChronoUnit.DAYS.between(from, to) + 1;
    int dataLen = (int) Math.max(320, Math.min(1600, requestedDays + 32));
    String payload = fetchKLinePayload(stockCode, dataLen);
    try {
      JsonNode root = objectMapper.readTree(payload);
      JsonNode stockNode = root.path("data").path(stockCode);
      JsonNode bars = stockNode.path("qfqday");
      if (!bars.isArray()) {
        bars = stockNode.path("day");
      }
      if (!bars.isArray()) {
        return List.of();
      }

      List<KLinePoint> points = new ArrayList<>();
      for (JsonNode bar : bars) {
        if (!bar.isArray() || bar.size() < 6) {
          continue;
        }
        LocalDate date = parseLocalDate(bar.get(0).asText(""));
        if (date == null || date.isBefore(from) || date.isAfter(to)) {
          continue;
        }
        BigDecimal open = parseDecimalValue(bar.get(1).asText(""));
        BigDecimal close = parseDecimalValue(bar.get(2).asText(""));
        BigDecimal high = parseDecimalValue(bar.get(3).asText(""));
        BigDecimal low = parseDecimalValue(bar.get(4).asText(""));
        Long volumeLots = parseLongValue(bar.get(5).asText(""));
        if (open == null || close == null || high == null || low == null) {
          continue;
        }

        long volume = normalizeVolume(volumeLots);
        KLinePoint point = new KLinePoint();
        point.setDate(date);
        point.setOpen(open);
        point.setClose(close);
        point.setHigh(high);
        point.setLow(low);
        point.setVolume(volume);
        BigDecimal avgPrice = open.add(close).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
        point.setAmount(avgPrice.multiply(BigDecimal.valueOf(volume)).setScale(2, RoundingMode.HALF_UP));
        points.add(point);
      }
      return points;
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to parse Tencent historical kline", ex);
    }
  }

  private String fetchKLinePayload(String stockCode, int dataLen) {
    try {
      String param = stockCode + ",day,,," + dataLen + ",qfq";
      String url = TENCENT_KLINE_URL + URLEncoder.encode(param, StandardCharsets.UTF_8);
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .timeout(Duration.ofSeconds(3))
              .header("Accept", "application/json,text/plain,*/*")
              .header(
                  "User-Agent",
                  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
              .header("Referer", "https://gu.qq.com")
              .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
              .GET()
              .build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() >= 400) {
        throw new IllegalStateException("Tencent kline response status is " + response.statusCode());
      }
      return response.body() == null ? "{}" : response.body();
    } catch (IOException | InterruptedException ex) {
      if (ex instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new IllegalStateException("Failed to request Tencent historical kline", ex);
    }
  }

  private List<KLinePoint> aggregate(List<KLinePoint> dailyPoints, KLinePeriod period) {
    if (period == KLinePeriod.DAILY || dailyPoints.isEmpty()) {
      return dailyPoints;
    }

    Map<LocalDate, KLineAccumulator> grouped = new LinkedHashMap<>();
    for (KLinePoint point : dailyPoints) {
      if (point == null || point.getDate() == null) {
        continue;
      }
      LocalDate bucket =
          switch (period) {
            case WEEKLY -> point.getDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTHLY -> point.getDate().withDayOfMonth(1);
            default -> point.getDate();
          };
      grouped.computeIfAbsent(bucket, ignored -> new KLineAccumulator()).accept(point);
    }
    return grouped.values().stream().map(KLineAccumulator::toPoint).toList();
  }

  private LocalDate parseLocalDate(String text) {
    if (text == null || text.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(text.trim());
    } catch (Exception ex) {
      return null;
    }
  }

  private BigDecimal parseDecimalValue(String text) {
    if (text == null || text.isBlank() || "-".equals(text.trim())) {
      return null;
    }
    try {
      return new BigDecimal(text.trim());
    } catch (Exception ex) {
      return null;
    }
  }

  private Long parseLongValue(String text) {
    if (text == null || text.isBlank() || "-".equals(text.trim())) {
      return null;
    }
    try {
      return new BigDecimal(text.trim()).longValue();
    } catch (Exception ex) {
      return null;
    }
  }

  private long normalizeVolume(Long volumeLots) {
    if (volumeLots == null || volumeLots <= 0L) {
      return 0L;
    }
    if (volumeLots > Long.MAX_VALUE / 100L) {
      return Long.MAX_VALUE;
    }
    return volumeLots * 100L;
  }

  private static final class KLineAccumulator {

    private LocalDate firstDate;
    private BigDecimal open;
    private BigDecimal close;
    private BigDecimal high;
    private BigDecimal low;
    private long volume;
    private BigDecimal amount = BigDecimal.ZERO;

    void accept(KLinePoint point) {
      if (firstDate == null) {
        firstDate = point.getDate();
        open = point.getOpen();
        high = point.getHigh();
        low = point.getLow();
      }
      close = point.getClose();
      if (point.getHigh() != null && (high == null || point.getHigh().compareTo(high) > 0)) {
        high = point.getHigh();
      }
      if (point.getLow() != null && (low == null || point.getLow().compareTo(low) < 0)) {
        low = point.getLow();
      }
      volume += point.getVolume() == null ? 0L : point.getVolume();
      if (point.getAmount() != null) {
        amount = amount.add(point.getAmount());
      }
    }

    KLinePoint toPoint() {
      KLinePoint point = new KLinePoint();
      point.setDate(firstDate);
      point.setOpen(open);
      point.setClose(close);
      point.setHigh(high);
      point.setLow(low);
      point.setVolume(volume);
      point.setAmount(amount);
      return point;
    }
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
    LocalDateTime sourceTs = LocalDateTime.now();
    snapshot.setTimestamp(sourceTs);
    snapshot.setSource("TENCENT");
    snapshot.setSourceTimestamp(sourceTs);
    return snapshot;
  }

  private List<KLinePoint> buildSyntheticKLine(
      String stockCode, KLinePeriod period, LocalDate from, LocalDate to, QuoteSnapshot latest) {
    List<KLinePoint> points = new ArrayList<>();
    Random random = new Random(buildSeed(stockCode, period, from, to));
    LocalDate cursor = from;
    BigDecimal previousClose =
        latest.getClosePrice() != null
            ? latest.getClosePrice()
            : latest.getCurrentPrice() != null ? latest.getCurrentPrice() : BigDecimal.ONE;
    long baseVolume = latest.getVolume() == null ? 120000L : Math.max(60000L, latest.getVolume());
    int index = 0;

    while (!cursor.isAfter(to)) {
      double trendWave = Math.sin((index + (stockCode.hashCode() & 63)) / 6.0d) * 0.010d;
      double randomShock = (random.nextDouble() - 0.5d) * 0.025d;
      double closeDrift = trendWave + randomShock;
      double openDrift = (random.nextDouble() - 0.5d) * 0.007d;

      BigDecimal open = applyRatio(previousClose, openDrift);
      BigDecimal close = applyRatio(open, closeDrift);
      BigDecimal body = close.subtract(open).abs();
      BigDecimal minBody = open.multiply(BigDecimal.valueOf(0.0015));
      if (body.compareTo(minBody) < 0) {
        close = close.add(close.compareTo(open) >= 0 ? minBody : minBody.negate());
      }

      BigDecimal wickUnit =
          open.multiply(BigDecimal.valueOf(0.004d + random.nextDouble() * 0.012d)).abs();
      BigDecimal high =
          open.max(close)
              .add(wickUnit.multiply(BigDecimal.valueOf(0.8d + random.nextDouble() * 0.9d)));
      BigDecimal low =
          open.min(close)
              .subtract(wickUnit.multiply(BigDecimal.valueOf(0.8d + random.nextDouble() * 0.9d)))
              .max(new BigDecimal("0.01"));

      long volume =
          Math.max(
              10000L,
              Math.round(
                  baseVolume * (0.55d + Math.abs(closeDrift) * 16d + random.nextDouble() * 0.85d)));

      KLinePoint point = new KLinePoint();
      point.setDate(cursor);
      point.setOpen(open.setScale(2, RoundingMode.HALF_UP));
      point.setClose(close.setScale(2, RoundingMode.HALF_UP));
      point.setHigh(high.setScale(2, RoundingMode.HALF_UP));
      point.setLow(low.setScale(2, RoundingMode.HALF_UP));
      point.setVolume(volume);
      BigDecimal avgPrice = open.add(close).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
      point.setAmount(avgPrice.multiply(BigDecimal.valueOf(volume)).setScale(2, RoundingMode.HALF_UP));
      points.add(point);

      previousClose = close.max(new BigDecimal("0.01"));
      cursor = nextCursor(cursor, period);
      index++;
    }
    return points;
  }

  private BigDecimal applyRatio(BigDecimal base, double drift) {
    BigDecimal ratio = BigDecimal.valueOf(1.0d + drift);
    BigDecimal next = base.multiply(ratio);
    return next.compareTo(new BigDecimal("0.01")) < 0 ? new BigDecimal("0.01") : next;
  }

  private long buildSeed(String stockCode, KLinePeriod period, LocalDate from, LocalDate to) {
    long seed = 1469598103934665603L;
    seed = seed * 1099511628211L ^ stockCode.hashCode();
    seed = seed * 1099511628211L ^ period.ordinal();
    seed = seed * 1099511628211L ^ from.toEpochDay();
    seed = seed * 1099511628211L ^ to.toEpochDay();
    return seed;
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
    if (code.startsWith("sh") || code.startsWith("sz") || code.startsWith("bj")) {
      return code;
    }
    if (code.matches("^[569]\\d{5}$")) {
      return "sh" + code;
    }
    if (code.matches("^[03]\\d{5}$")) {
      return "sz" + code;
    }
    if (code.matches("^[48]\\d{5}$")) {
      return "bj" + code;
    }
    return code;
  }
}
