package com.lzbsdsg.stocksimulation.common.filter;

import com.lzbsdsg.stocksimulation.common.util.TraceIdUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** 为每个请求注入 traceId，并写入响应头与 MDC。 */
@Component
public class TraceIdFilter extends OncePerRequestFilter {

  private static final String TRACE_HEADER = "X-Trace-Id";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String traceId = request.getHeader(TRACE_HEADER);
    if (traceId == null || traceId.isBlank()) {
      traceId = TraceIdUtil.generate();
    } else {
      TraceIdUtil.set(traceId.trim());
    }
    response.setHeader(TRACE_HEADER, TraceIdUtil.get());
    try {
      filterChain.doFilter(request, response);
    } finally {
      TraceIdUtil.clear();
    }
  }
}
