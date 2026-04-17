package com.lzbsdsg.stocksimulation.market.domain.service;

import com.lzbsdsg.stocksimulation.market.domain.entity.QuoteSnapshot;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/** 行情融合策略：基于新鲜度与字段完整度决定是否覆盖缓存。 */
public final class QuoteMergePolicy {

  private QuoteMergePolicy() {
  }

  public static boolean shouldReplace(QuoteSnapshot existing, QuoteSnapshot candidate) {
    if (candidate == null) {
      return false;
    }
    if (existing == null) {
      return true;
    }

    LocalDateTime existingTs = effectiveTimestamp(existing);
    LocalDateTime candidateTs = effectiveTimestamp(candidate);

    if (candidateTs != null && existingTs != null) {
      if (candidateTs.isAfter(existingTs)) {
        return true;
      }
      if (candidateTs.isBefore(existingTs)) {
        return false;
      }
    } else if (candidateTs != null) {
      return true;
    }

    int existingScore = completenessScore(existing);
    int candidateScore = completenessScore(candidate);
    if (candidateScore > existingScore) {
      return true;
    }
    if (candidateScore < existingScore) {
      return false;
    }

    return isMeaningfulChange(existing, candidate);
  }

  public static boolean isMeaningfulChange(QuoteSnapshot existing, QuoteSnapshot candidate) {
    if (existing == null || candidate == null) {
      return true;
    }
    return !Objects.equals(existing.getCurrentPrice(), candidate.getCurrentPrice())
        || !Objects.equals(existing.getOpenPrice(), candidate.getOpenPrice())
        || !Objects.equals(existing.getClosePrice(), candidate.getClosePrice())
        || !Objects.equals(existing.getHighPrice(), candidate.getHighPrice())
        || !Objects.equals(existing.getLowPrice(), candidate.getLowPrice())
        || !Objects.equals(existing.getVolume(), candidate.getVolume())
        || !Objects.equals(existing.getAmount(), candidate.getAmount())
        || !Objects.equals(existing.getChangePercent(), candidate.getChangePercent())
        || !Objects.equals(existing.getUpperLimitPrice(), candidate.getUpperLimitPrice())
        || !Objects.equals(existing.getLowerLimitPrice(), candidate.getLowerLimitPrice())
        || !Objects.equals(existing.getStockName(), candidate.getStockName())
        || !Objects.equals(existing.getSource(), candidate.getSource());
  }

  public static int completenessScore(QuoteSnapshot quote) {
    if (quote == null) {
      return 0;
    }
    int score = 0;
    if (quote.getCurrentPrice() != null) {
      score++;
    }
    if (quote.getOpenPrice() != null) {
      score++;
    }
    if (quote.getClosePrice() != null) {
      score++;
    }
    if (quote.getHighPrice() != null) {
      score++;
    }
    if (quote.getLowPrice() != null) {
      score++;
    }
    if (quote.getVolume() != null) {
      score++;
    }
    if (quote.getAmount() != null) {
      score++;
    }
    if (quote.getChangePercent() != null) {
      score++;
    }
    if (quote.getUpperLimitPrice() != null) {
      score++;
    }
    if (quote.getLowerLimitPrice() != null) {
      score++;
    }
    return score;
  }

  private static LocalDateTime effectiveTimestamp(QuoteSnapshot quote) {
    if (quote == null) {
      return null;
    }
    return quote.getSourceTimestamp() != null ? quote.getSourceTimestamp() : quote.getTimestamp();
  }

  public static BigDecimal nullableDiff(BigDecimal current, BigDecimal previous) {
    if (current == null || previous == null) {
      return null;
    }
    return current.subtract(previous);
  }
}
