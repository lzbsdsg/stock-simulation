package com.lzbsdsg.stocksimulation.config;

import java.math.BigDecimal;
import java.time.LocalTime;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 交易规则配置 Bean
 *
 * <p>从 application.yml 中读取，支持运行时修改。
 */
@Configuration
@ConfigurationProperties(prefix = "trade.rule")
public class TradeRuleConfig {

  /** 上午开盘时间 */
  private LocalTime morningStart = LocalTime.of(9, 30);

  /** 上午收盘时间 */
  private LocalTime morningEnd = LocalTime.of(11, 30);

  /** 下午开盘时间 */
  private LocalTime afternoonStart = LocalTime.of(13, 0);

  /** 下午收盘时间 */
  private LocalTime afternoonEnd = LocalTime.of(15, 0);

  /** 最小交易单位 */
  private int minQuantity = 100;

  /** 普通股涨跌停比例 */
  private BigDecimal normalPriceLimit = new BigDecimal("0.10");

  /** ST股涨跌停比例 */
  private BigDecimal stPriceLimit = new BigDecimal("0.05");

  /** 科创板/创业板涨跌停比例 */
  private BigDecimal gemStarPriceLimit = new BigDecimal("0.20");

  // ==================== Getters & Setters ====================

  public LocalTime getMorningStart() {
    return morningStart;
  }

  public void setMorningStart(LocalTime morningStart) {
    this.morningStart = morningStart;
  }

  public LocalTime getMorningEnd() {
    return morningEnd;
  }

  public void setMorningEnd(LocalTime morningEnd) {
    this.morningEnd = morningEnd;
  }

  public LocalTime getAfternoonStart() {
    return afternoonStart;
  }

  public void setAfternoonStart(LocalTime afternoonStart) {
    this.afternoonStart = afternoonStart;
  }

  public LocalTime getAfternoonEnd() {
    return afternoonEnd;
  }

  public void setAfternoonEnd(LocalTime afternoonEnd) {
    this.afternoonEnd = afternoonEnd;
  }

  public int getMinQuantity() {
    return minQuantity;
  }

  public void setMinQuantity(int minQuantity) {
    this.minQuantity = minQuantity;
  }

  public BigDecimal getNormalPriceLimit() {
    return normalPriceLimit;
  }

  public void setNormalPriceLimit(BigDecimal normalPriceLimit) {
    this.normalPriceLimit = normalPriceLimit;
  }

  public BigDecimal getStPriceLimit() {
    return stPriceLimit;
  }

  public void setStPriceLimit(BigDecimal stPriceLimit) {
    this.stPriceLimit = stPriceLimit;
  }

  public BigDecimal getGemStarPriceLimit() {
    return gemStarPriceLimit;
  }

  public void setGemStarPriceLimit(BigDecimal gemStarPriceLimit) {
    this.gemStarPriceLimit = gemStarPriceLimit;
  }
}
