package com.lzbsdsg.stocksimulation.market.application.vo;

/** 延迟指标聚合视图。 */
public record MarketLatencyMetricVO(
        String metric,
        long count,
        Double meanMs,
        Double maxMs,
        Double p95Ms,
        Double p99Ms) {
}
