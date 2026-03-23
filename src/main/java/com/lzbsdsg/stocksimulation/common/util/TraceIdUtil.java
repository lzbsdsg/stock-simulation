package com.lzbsdsg.stocksimulation.common.util;

import java.util.UUID;
import org.slf4j.MDC;

/**
 * TraceId 工具类
 *
 * <p>用于请求链路追踪，在日志中统一输出 traceId。
 */
public final class TraceIdUtil {

  public static final String TRACE_ID_KEY = "traceId";

  private TraceIdUtil() {}

  /** 生成并设置 traceId 到 MDC */
  public static String generate() {
    String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    MDC.put(TRACE_ID_KEY, traceId);
    return traceId;
  }

  /** 获取当前 traceId */
  public static String get() {
    return MDC.get(TRACE_ID_KEY);
  }

  /** 清理 traceId */
  public static void clear() {
    MDC.remove(TRACE_ID_KEY);
  }
}
