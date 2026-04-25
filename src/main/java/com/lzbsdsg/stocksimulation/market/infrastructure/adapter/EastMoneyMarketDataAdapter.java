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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 东方财富实时行情适配器。 */
@Slf4j
@Order(3)
@Component
public class EastMoneyMarketDataAdapter implements MarketDataProvider {

  private static final String EAST_MONEY_BASE_URL = "https://push2.eastmoney.com/api/qt";
  private static final String OFFICIAL_UT = "fa5fd1943c7b386f172d6893dbfba10b";
  private static final String QUOTE_FIELDS = "f12,f14,f2,f3,f4,f5,f6,f15,f16,f17,f18";
  private static final String USER_AGENT =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)"
          + " Chrome/124.0.0.0 Safari/537.36";

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  public EastMoneyMarketDataAdapter() {
    this(
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build(),
        new ObjectMapper());
  }

  EastMoneyMarketDataAdapter(HttpClient httpClient, ObjectMapper objectMapper) {
    this.httpClient = httpClient;
    this.objectMapper = objectMapper;
  }

  @Override
  public QuoteSnapshot getQuote(String stockCode) {
    String secId = toSecId(stockCode);
    if (secId.isBlank()) {
      throw new IllegalStateException("Unsupported stock code for EastMoney: " + stockCode);
    }

    JsonNode root =
        requestJson(
            Map.of(
                "ut", OFFICIAL_UT,
                "fltt", "2",
                "invt", "2",
                "fields", QUOTE_FIELDS,
                "secids", secId));
    for (JsonNode item : readDiffArray(root)) {
      QuoteSnapshot parsed = parseQuote(item);
      if (parsed != null) {
        return parsed;
      }
    }
    throw new IllegalStateException("EastMoney quote payload has no valid line for " + stockCode);
  }

  @Override
  public List<KLinePoint> getKLine(
      String stockCode, KLinePeriod period, LocalDate from, LocalDate to) {
    if (from == null || to == null || from.isAfter(to)) {
      return List.of();
    }

    String normalizedCode = normalizeStockCode(stockCode);
    QuoteSnapshot latest = getQuote(normalizedCode);
    return buildSyntheticKLine(normalizedCode, period, from, to, latest);
  }

  @Override
  public List<QuoteSnapshot> batchGetQuotes(List<String> stockCodes) {
    List<String> secIds =
        stockCodes.stream().map(this::toSecId).filter(code -> !code.isBlank()).distinct().toList();
    if (secIds.isEmpty()) {
      return List.of();
    }

    JsonNode root =
        requestJson(
            Map.of(
                "ut",
                OFFICIAL_UT,
                "fltt",
                "2",
                "invt",
                "2",
                "fields",
                QUOTE_FIELDS,
                "secids",
                String.join(",", secIds)));
    List<QuoteSnapshot> result = new ArrayList<>();
    for (JsonNode item : readDiffArray(root)) {
      QuoteSnapshot parsed = parseQuote(item);
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
      log.warn("EastMoney provider health-check failed: {}", ex.getMessage());
      return false;
    }
  }

  private QuoteSnapshot parseQuote(JsonNode item) {
    String rawCode = textValue(item, "f12");
    String rawName = textValue(item, "f14");
    if (rawCode.isBlank() || rawName.isBlank()) {
      return null;
    }

    QuoteSnapshot snapshot = new QuoteSnapshot();
    snapshot.setStockCode(normalizeStockCode(rawCode));
    snapshot.setStockName(rawName);
    snapshot.setCurrentPrice(decimalValue(item.get("f2")));
    snapshot.setChangePercent(decimalValue(item.get("f3")));
    snapshot.setOpenPrice(decimalValue(item.get("f17")));
    snapshot.setClosePrice(decimalValue(item.get("f18")));
    snapshot.setHighPrice(decimalValue(item.get("f15")));
    snapshot.setLowPrice(decimalValue(item.get("f16")));
    snapshot.setVolume(longValue(item.get("f5")));
    snapshot.setAmount(decimalValue(item.get("f6")));

    if (snapshot.getHighPrice() == null) {
      snapshot.setHighPrice(snapshot.getCurrentPrice());
    }
    if (snapshot.getLowPrice() == null) {
      snapshot.setLowPrice(snapshot.getCurrentPrice());
    }
    if (snapshot.getOpenPrice() == null) {
      snapshot.setOpenPrice(snapshot.getClosePrice());
    }
    if (snapshot.getClosePrice() != null) {
      snapshot.setUpperLimitPrice(
          snapshot
              .getClosePrice()
              .multiply(BigDecimal.valueOf(1.10))
              .setScale(2, RoundingMode.HALF_UP));
      snapshot.setLowerLimitPrice(
          snapshot
              .getClosePrice()
              .multiply(BigDecimal.valueOf(0.90))
              .setScale(2, RoundingMode.HALF_UP));
    }

    LocalDateTime sourceTs = LocalDateTime.now();
    snapshot.setTimestamp(sourceTs);
    snapshot.setSource("EASTMONEY");
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
    long baseVolume = latest.getVolume() == null ? 110000L : Math.max(50000L, latest.getVolume());
    int index = 0;

    while (!cursor.isAfter(to)) {
      double trendWave = Math.sin((index + (stockCode.hashCode() & 47)) / 6.0d) * 0.009d;
      double randomShock = (random.nextDouble() - 0.5d) * 0.02d;
      double closeDrift = trendWave + randomShock;
      double openDrift = (random.nextDouble() - 0.5d) * 0.006d;

      BigDecimal open = applyRatio(previousClose, openDrift);
      BigDecimal close = applyRatio(open, closeDrift);
      BigDecimal body = close.subtract(open).abs();
      BigDecimal minBody = open.multiply(BigDecimal.valueOf(0.0015));
      if (body.compareTo(minBody) < 0) {
        close = close.add(close.compareTo(open) >= 0 ? minBody : minBody.negate());
      }

      BigDecimal wickUnit =
          open.multiply(BigDecimal.valueOf(0.004d + random.nextDouble() * 0.01d)).abs();
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
                  baseVolume * (0.55d + Math.abs(closeDrift) * 17d + random.nextDouble() * 0.85d)));

      KLinePoint point = new KLinePoint();
      point.setDate(cursor);
      point.setOpen(open.setScale(2, RoundingMode.HALF_UP));
      point.setClose(close.setScale(2, RoundingMode.HALF_UP));
      point.setHigh(high.setScale(2, RoundingMode.HALF_UP));
      point.setLow(low.setScale(2, RoundingMode.HALF_UP));
      point.setVolume(volume);
      BigDecimal avgPrice = open.add(close).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
      point.setAmount(
          avgPrice.multiply(BigDecimal.valueOf(volume)).setScale(2, RoundingMode.HALF_UP));
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

  private JsonNode requestJson(Map<String, String> params) {
    String url = buildUrl(params);
    Exception lastError = null;
    for (int attempt = 1; attempt <= 2; attempt++) {
      try {
        HttpRequest request =
            HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(4))
                .header("Accept", "application/json,text/plain,*/*")
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://quote.eastmoney.com/")
                .header("Origin", "https://quote.eastmoney.com")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Connection", "keep-alive")
                .GET()
                .build();
        HttpResponse<String> response =
            httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
          throw new IllegalStateException("EastMoney response status is " + response.statusCode());
        }
        return objectMapper.readTree(extractJsonPayload(response.body()));
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Failed to request EastMoney quote", ex);
      } catch (IOException | RuntimeException ex) {
        lastError = ex;
      }
    }
    throw new IllegalStateException("Failed to request EastMoney quote", lastError);
  }

  private String buildUrl(Map<String, String> params) {
    StringBuilder builder = new StringBuilder(EAST_MONEY_BASE_URL).append("/ulist.np/get");
    if (!params.isEmpty()) {
      builder.append('?');
      boolean first = true;
      for (Map.Entry<String, String> entry : params.entrySet()) {
        if (!first) {
          builder.append('&');
        }
        first = false;
        builder.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
      }
    }
    return builder.toString();
  }

  private List<JsonNode> readDiffArray(JsonNode root) {
    JsonNode diff = root.path("data").path("diff");
    if (!diff.isArray()) {
      return List.of();
    }
    List<JsonNode> result = new ArrayList<>();
    diff.forEach(result::add);
    return result;
  }

  private String textValue(JsonNode node, String fieldName) {
    JsonNode field = node == null ? null : node.get(fieldName);
    if (field == null || field.isNull()) {
      return "";
    }
    return field.asText("").trim();
  }

  private BigDecimal decimalValue(JsonNode node) {
    if (node == null || node.isNull()) {
      return null;
    }
    String raw = node.asText("").trim();
    if (raw.isBlank() || "-".equals(raw)) {
      return null;
    }
    try {
      return new BigDecimal(raw);
    } catch (Exception ex) {
      return null;
    }
  }

  private Long longValue(JsonNode node) {
    BigDecimal decimal = decimalValue(node);
    return decimal == null ? null : decimal.longValue();
  }

  private String toSecId(String stockCode) {
    String normalized = normalizeStockCode(stockCode);
    if (normalized.startsWith("sh")) {
      return "1." + normalized.substring(2);
    }
    if (normalized.startsWith("sz")) {
      return "0." + normalized.substring(2);
    }
    return "";
  }

  private String normalizeStockCode(String rawCode) {
    String code = rawCode == null ? "" : rawCode.trim().toLowerCase(Locale.ROOT);
    if (code.isBlank()) {
      return code;
    }
    if (code.startsWith("sh") || code.startsWith("sz") || code.startsWith("bj")) {
      return code;
    }
    if (code.startsWith("6") || code.startsWith("5") || code.startsWith("9")) {
      return "sh" + code;
    }
    if (code.startsWith("0") || code.startsWith("3")) {
      return "sz" + code;
    }
    if (code.startsWith("8") || code.startsWith("4")) {
      return "bj" + code;
    }
    return code;
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

  private String encode(String value) {
    return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
  }
}
