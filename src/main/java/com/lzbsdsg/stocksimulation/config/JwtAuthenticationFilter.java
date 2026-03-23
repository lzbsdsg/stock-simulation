package com.lzbsdsg.stocksimulation.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT 认证过滤器
 *
 * <p>从请求头获取 Bearer Token，校验有效性后注入 SecurityContext。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  // TODO: 注入 JwtTokenProvider

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    // TODO: 实现 JWT 校验逻辑
    // 1. 从 Header 提取 "Authorization: Bearer <token>"
    // 2. 校验 token 有效性（解析 + 黑名单检查）
    // 3. 解析用户信息，创建 Authentication 对象
    // 4. 设置 SecurityContextHolder.getContext().setAuthentication(...)

    filterChain.doFilter(request, response);
  }
}
