package com.lzbsdsg.stocksimulation.market.application.vo;

import java.time.LocalDateTime;

/** 行情实时观测指标。 */
public record MarketRealtimeMetricsVO(
    LocalDateTime sampledAt,
    long activeCodeCount,
    int lastIngestCodeCount,
    int lastPublishedQuoteCount,
    long lastIngestDurationMs,
    long wsActiveConnections,
    long wsQueuedTasks,
    boolean wsDegradedMode,
    double wsDroppedTotal,
    MarketLatencyMetricVO ingestCycleLatency,
    MarketLatencyMetricVO pubSubFanoutLatency,
    MarketLatencyMetricVO wsQueueLatency,
    MarketLatencyMetricVO wsPushLatency) {}
