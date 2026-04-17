package com.lzbsdsg.stocksimulation.market.infrastructure.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lzbsdsg.stocksimulation.market.application.vo.MarketIndexQuoteVO;
import com.lzbsdsg.stocksimulation.market.application.vo.MarketRankBoardVO;
import com.lzbsdsg.stocksimulation.market.application.vo.QuoteVO;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 东方财富榜单与大盘网关 */
@Slf4j
@Component
public class EastMoneyOfficialBoardGateway {

  private static final String EAST_MONEY_BASE_URL = "https://push2.eastmoney.com/api/qt";
    private static final String SINA_RANK_URL =
      "https://vip.stock.finance.sina.com.cn/quotes_service/api/json_v2.php/"
        + "Market_Center.getHQNodeData";
  private static final String USER_AGENT =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)"
          + " Chrome/124.0.0.0 Safari/537.36";
    private static final Charset GBK = Charset.forName("GBK");
  private static final String INDEX_FIELDS = "f12,f14,f2,f3,f4,f5,f6";
  private static final String RANK_FIELDS = "f12,f14,f2,f3,f4,f5,f6,f15,f16,f18";
  private static final String INDEX_SEC_IDS = "1.000001,0.399001,1.000300";
  private static final String A_STOCK_FS = "m:0+t:6,m:0+t:13,m:0+t:80,m:1+t:2,m:1+t:23";
  private static final String OFFICIAL_UT = "fa5fd1943c7b386f172d6893dbfba10b";

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  private volatile List<MarketIndexQuoteVO> cachedIndexQuotes = List.of();
  private volatile MarketRankBoardVO cachedRankBoard = new MarketRankBoardVO(List.of(), List.of());

  public EastMoneyOfficialBoardGateway() {
    this(
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build(),
        new ObjectMapper());
  }

  EastMoneyOfficialBoardGateway(HttpClient httpClient, ObjectMapper objectMapper) {
    this.httpClient = httpClient;
    this.objectMapper = objectMapper;
  }

  public synchronized List<MarketIndexQuoteVO> fetchIndexQuotes() {
    try {
      JsonNode root =
          requestJson(
              "/ulist.np/get",
              Map.of(
                  "ut", OFFICIAL_UT,
                  "fltt", "2",
                  "invt", "2",
                  "secids", INDEX_SEC_IDS,
                  "fields", INDEX_FIELDS));
      List<MarketIndexQuoteVO> parsed = parseIndexQuotes(root);
      if (!parsed.isEmpty()) {
        cachedIndexQuotes = List.copyOf(parsed);
      }
      return parsed.isEmpty() ? cachedIndexQuotes : parsed;
    } catch (Exception ex) {
      if (!cachedIndexQuotes.isEmpty()) {
        log.warn("Fetch official index quotes failed, fallback to cache: {}", ex.getMessage());
        return cachedIndexQuotes;
      }
      throw new IllegalStateException("Failed to fetch official index quotes", ex);
    }
  }

  public synchronized MarketRankBoardVO fetchRankBoard(int limit) {
    int safeLimit = Math.max(1, Math.min(limit, 20));
    try {
      Map<String, String> baseParams = new LinkedHashMap<>();
      baseParams.put("pz", String.valueOf(safeLimit));
      baseParams.put("np", "1");
      baseParams.put("ut", OFFICIAL_UT);
      baseParams.put("fltt", "2");
      baseParams.put("invt", "2");
      baseParams.put("fs", A_STOCK_FS);
      baseParams.put("fields", RANK_FIELDS);

      Map<String, String> riseParams = new LinkedHashMap<>(baseParams);
      riseParams.put("pn", "1");
      riseParams.put("po", "1");
      riseParams.put("fid", "f3");

      Map<String, String> fallParams = new LinkedHashMap<>(baseParams);
      fallParams.put("pn", "1");
      fallParams.put("po", "0");
      fallParams.put("fid", "f3");

      JsonNode riseRoot = requestJson("/clist/get", riseParams);
      JsonNode fallRoot = requestJson("/clist/get", fallParams);

      MarketRankBoardVO board =
          new MarketRankBoardVO(parseRankQuotes(riseRoot), parseRankQuotes(fallRoot));
      if (!board.gainers().isEmpty() || !board.losers().isEmpty()) {
        cachedRankBoard = board;
      }
      if (!board.gainers().isEmpty() || !board.losers().isEmpty()) {
        return board;
      }

      MarketRankBoardVO sinaBoard = fetchSinaRankBoard(safeLimit);
      if (!sinaBoard.gainers().isEmpty() || !sinaBoard.losers().isEmpty()) {
        cachedRankBoard = sinaBoard;
        return sinaBoard;
      }
      return cachedRankBoard;
    } catch (Exception ex) {
      MarketRankBoardVO sinaBoard = fetchSinaRankBoard(safeLimit);
      if (!sinaBoard.gainers().isEmpty() || !sinaBoard.losers().isEmpty()) {
        cachedRankBoard = sinaBoard;
        log.warn("Fetch EastMoney rank board failed, fallback to Sina rank board: {}", ex.getMessage());
        return sinaBoard;
      }
      if (!cachedRankBoard.gainers().isEmpty() || !cachedRankBoard.losers().isEmpty()) {
        log.warn("Fetch official rank board failed, fallback to cache: {}", ex.getMessage());
        return cachedRankBoard;
      }
      throw new IllegalStateException("Failed to fetch official rank board", ex);
    }
  }

  private MarketRankBoardVO fetchSinaRankBoard(int limit) {
    try {
      List<QuoteVO> gainers = fetchSinaRankList(limit, false);
      List<QuoteVO> losers = fetchSinaRankList(limit, true);
      return new MarketRankBoardVO(gainers, losers);
    } catch (Exception ex) {
      log.warn("Fetch Sina rank board failed: {}", ex.getMessage());
      return new MarketRankBoardVO(List.of(), List.of());
    }
  }

  private List<QuoteVO> fetchSinaRankList(int limit, boolean asc) {
    try {
      Map<String, String> params = new LinkedHashMap<>();
      params.put("page", "1");
      params.put("num", String.valueOf(limit));
      params.put("sort", "changepercent");
      params.put("asc", asc ? "1" : "0");
      params.put("node", "hs_a");
      params.put("symbol", "");
      params.put("_s_r_a", "sort");

      String url = buildAbsoluteUrl(SINA_RANK_URL, params);
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .timeout(Duration.ofSeconds(4))
              .header("Accept", "application/json,text/plain,*/*")
              .header("User-Agent", USER_AGENT)
              .header("Referer", "https://finance.sina.com.cn/")
              .header("Connection", "keep-alive")
              .GET()
              .build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString(GBK));
      if (response.statusCode() >= 400) {
        throw new IllegalStateException("Sina rank response status is " + response.statusCode());
      }

      JsonNode root = objectMapper.readTree(response.body());
      if (!root.isArray()) {
        return List.of();
      }

      List<QuoteVO> result = new ArrayList<>();
      for (JsonNode item : root) {
        String rawSymbol = item.path("symbol").asText("").trim();
        String rawCode = item.path("code").asText("").trim();
        String stockName = item.path("name").asText("").trim();
        String effectiveCode = !rawSymbol.isBlank() ? rawSymbol : rawCode;
        if (effectiveCode.isBlank() || stockName.isBlank()) {
          continue;
        }

        result.add(
            new QuoteVO(
                normalizeAStockCode(effectiveCode),
                stockName,
                decimalValue(item.get("trade")),
                decimalValue(item.get("open")),
                decimalValue(item.get("settlement")),
                decimalValue(item.get("high")),
                decimalValue(item.get("low")),
                longValue(item.get("volume")),
                decimalValue(item.get("amount")),
                decimalValue(item.get("changepercent")),
                LocalDateTime.now()));
      }
      return result;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Sina rank request interrupted", ex);
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to parse Sina rank response", ex);
    }
  }

  private List<MarketIndexQuoteVO> parseIndexQuotes(JsonNode root) {
    List<MarketIndexQuoteVO> result = new ArrayList<>();
    for (JsonNode item : readDiffArray(root)) {
      String rawCode = textValue(item, "f12");
      String rawName = textValue(item, "f14");
      if (rawCode.isBlank() || rawName.isBlank()) {
        continue;
      }
      result.add(
          new MarketIndexQuoteVO(
              normalizeIndexCode(rawCode, rawName),
              rawName,
              decimalValue(item.get("f2")),
              decimalValue(item.get("f4")),
              decimalValue(item.get("f3")),
              longValue(item.get("f5")),
              decimalValue(item.get("f6"))));
    }
    return result;
  }

  private List<QuoteVO> parseRankQuotes(JsonNode root) {
    List<QuoteVO> result = new ArrayList<>();
    for (JsonNode item : readDiffArray(root)) {
      String rawCode = textValue(item, "f12");
      String rawName = textValue(item, "f14");
      if (rawCode.isBlank() || rawName.isBlank()) {
        continue;
      }
      result.add(
          new QuoteVO(
              normalizeAStockCode(rawCode),
              rawName,
              decimalValue(item.get("f2")),
              null,
              decimalValue(item.get("f18")),
              decimalValue(item.get("f15")),
              decimalValue(item.get("f16")),
              longValue(item.get("f5")),
              decimalValue(item.get("f6")),
              decimalValue(item.get("f3")),
              LocalDateTime.now()));
    }
    return result;
  }

  private JsonNode requestJson(String path, Map<String, String> params) {
    String url = buildUrl(path, params);
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
        throw new IllegalStateException("Failed to request EastMoney official board", ex);
      } catch (IOException | RuntimeException ex) {
        lastError = ex;
      }
    }
    throw new IllegalStateException("Failed to request EastMoney official board", lastError);
  }

  private String buildUrl(String path, Map<String, String> params) {
    StringBuilder builder = new StringBuilder(EAST_MONEY_BASE_URL).append(path);
    appendQuery(builder, params);
    return builder.toString();
  }

  private String buildAbsoluteUrl(String baseUrl, Map<String, String> params) {
    StringBuilder builder = new StringBuilder(baseUrl);
    appendQuery(builder, params);
    return builder.toString();
  }

  private void appendQuery(StringBuilder builder, Map<String, String> params) {
    if (params.isEmpty()) {
      return;
    }
    builder.append('?');
    boolean first = true;
    for (Map.Entry<String, String> entry : params.entrySet()) {
      if (!first) {
        builder.append('&');
      }
      first = false;
      builder
          .append(encode(entry.getKey()))
          .append('=')
          .append(encode(entry.getValue()));
    }
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

  private String normalizeIndexCode(String rawCode, String rawName) {
    if (rawName.contains("上证")) {
      return "sh" + rawCode;
    }
    if (rawName.contains("深证")) {
      return "sz" + rawCode;
    }
    if (rawName.contains("沪深300")) {
      return "sh000300";
    }
    return normalizeAStockCode(rawCode);
  }

  private String normalizeAStockCode(String rawCode) {
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
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
