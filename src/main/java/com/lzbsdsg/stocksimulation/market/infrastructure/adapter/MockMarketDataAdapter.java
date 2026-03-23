package com.lzbsdsg.stocksimulation.market.infrastructure.adapter;

import com.lzbsdsg.stocksimulation.market.domain.entity.KLinePeriod;
import com.lzbsdsg.stocksimulation.market.domain.entity.KLinePoint;
import com.lzbsdsg.stocksimulation.market.domain.entity.QuoteSnapshot;
import com.lzbsdsg.stocksimulation.market.domain.gateway.MarketDataProvider;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Mock 行情数据适配器（开发/测试环境使用）
 *
 * <p>仅在 dev / test profile 下激活，生成随机模拟数据。
 */
@Slf4j
@Order(100)
@Component
@Profile({"dev", "test"})
public class MockMarketDataAdapter implements MarketDataProvider {

  private final Random random = new Random();

  @Override
  public QuoteSnapshot getQuote(String stockCode) {
    QuoteSnapshot snapshot = new QuoteSnapshot();
    snapshot.setStockCode(stockCode);
    snapshot.setStockName("模拟股票-" + stockCode);
    BigDecimal basePrice = BigDecimal.valueOf(10 + random.nextDouble() * 90);
    snapshot.setCurrentPrice(basePrice.setScale(2, java.math.RoundingMode.HALF_UP));
    snapshot.setOpenPrice(
        basePrice
            .multiply(BigDecimal.valueOf(0.98 + random.nextDouble() * 0.04))
            .setScale(2, java.math.RoundingMode.HALF_UP));
    snapshot.setClosePrice(
        basePrice
            .multiply(BigDecimal.valueOf(0.97 + random.nextDouble() * 0.06))
            .setScale(2, java.math.RoundingMode.HALF_UP));
    snapshot.setHighPrice(
        basePrice.multiply(BigDecimal.valueOf(1.02)).setScale(2, java.math.RoundingMode.HALF_UP));
    snapshot.setLowPrice(
        basePrice.multiply(BigDecimal.valueOf(0.96)).setScale(2, java.math.RoundingMode.HALF_UP));
    snapshot.setVolume((long) (random.nextInt(1000000) + 100000));
    snapshot.setAmount(basePrice.multiply(BigDecimal.valueOf(snapshot.getVolume())));
    snapshot.setChangePercent(
        BigDecimal.valueOf(-5 + random.nextDouble() * 10)
            .setScale(2, java.math.RoundingMode.HALF_UP));
    snapshot.setUpperLimitPrice(
        snapshot
            .getClosePrice()
            .multiply(BigDecimal.valueOf(1.10))
            .setScale(2, java.math.RoundingMode.HALF_UP));
    snapshot.setLowerLimitPrice(
        snapshot
            .getClosePrice()
            .multiply(BigDecimal.valueOf(0.90))
            .setScale(2, java.math.RoundingMode.HALF_UP));
    snapshot.setTimestamp(LocalDateTime.now());
    return snapshot;
  }

  @Override
  public List<KLinePoint> getKLine(
      String stockCode, KLinePeriod period, LocalDate from, LocalDate to) {
    List<KLinePoint> points = new ArrayList<>();
    LocalDate current = from;
    BigDecimal price = BigDecimal.valueOf(20 + random.nextDouble() * 80);
    while (!current.isAfter(to)) {
      KLinePoint point = new KLinePoint();
      point.setDate(current);
      point.setOpen(price.setScale(2, java.math.RoundingMode.HALF_UP));
      price = price.multiply(BigDecimal.valueOf(0.97 + random.nextDouble() * 0.06));
      point.setClose(price.setScale(2, java.math.RoundingMode.HALF_UP));
      point.setHigh(
          price.multiply(BigDecimal.valueOf(1.03)).setScale(2, java.math.RoundingMode.HALF_UP));
      point.setLow(
          price.multiply(BigDecimal.valueOf(0.97)).setScale(2, java.math.RoundingMode.HALF_UP));
      point.setVolume((long) (random.nextInt(500000) + 50000));
      point.setAmount(price.multiply(BigDecimal.valueOf(point.getVolume())));
      points.add(point);
      current = current.plusDays(1);
    }
    return points;
  }

  @Override
  public List<QuoteSnapshot> batchGetQuotes(List<String> stockCodes) {
    return stockCodes.stream().map(this::getQuote).toList();
  }

  @Override
  public boolean isAvailable() {
    return true;
  }
}
