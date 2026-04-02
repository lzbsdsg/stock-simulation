package com.lzbsdsg.stocksimulation.market.infrastructure.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lzbsdsg.stocksimulation.market.domain.entity.QuoteSnapshot;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/** 市场全量快照池（由定时抓取链路增量更新） */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketOverviewGateway {

  private static final String OVERVIEW_HASH_KEY = "market:overview:snapshots";
  private static final Duration OVERVIEW_TTL = Duration.ofHours(24);

  private final RedisTemplate<String, Object> redisTemplate;
  private final ObjectMapper objectMapper;

  public void upsertQuotes(List<QuoteSnapshot> quotes) {
    if (quotes == null || quotes.isEmpty()) {
      return;
    }

    for (QuoteSnapshot quote : quotes) {
      if (quote == null || quote.getStockCode() == null || quote.getStockCode().isBlank()) {
        continue;
      }
      String stockCode = quote.getStockCode().trim().toLowerCase();
      redisTemplate.opsForHash().put(OVERVIEW_HASH_KEY, stockCode, quote);
    }
    redisTemplate.expire(OVERVIEW_HASH_KEY, OVERVIEW_TTL);
  }

  public List<QuoteSnapshot> listQuotes() {
    List<Object> values = redisTemplate.opsForHash().values(OVERVIEW_HASH_KEY);
    if (values == null || values.isEmpty()) {
      return List.of();
    }

    List<QuoteSnapshot> snapshots = new ArrayList<>(values.size());
    for (Object raw : values) {
      try {
        if (raw instanceof QuoteSnapshot quote) {
          snapshots.add(quote);
        } else if (raw != null) {
          snapshots.add(objectMapper.convertValue(raw, QuoteSnapshot.class));
        }
      } catch (Exception ex) {
        log.warn("Skip malformed market overview snapshot: {}", ex.getMessage());
      }
    }
    return snapshots;
  }
}
