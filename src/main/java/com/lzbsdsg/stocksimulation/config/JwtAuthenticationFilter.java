package com.lzbsdsg.stocksimulation.config;

import com.lzbsdsg.stocksimulation.auth.infrastructure.gateway.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT 认证过滤器
 *
 * <p>从请求头获取 Bearer Token，校验有效性后注入 SecurityContext。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtTokenProvider jwtTokenProvider;

  public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
    this.jwtTokenProvider = jwtTokenProvider;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String authorization = request.getHeader("Authorization");
    if (authorization != null && authorization.startsWith("Bearer ")) {
      String token = authorization.substring(7);
      if (jwtTokenProvider.validateToken(token) && jwtTokenProvider.isAccessToken(token)) {
        Long userId = jwtTokenProvider.getUserIdFromToken(token);
        String role = jwtTokenProvider.getRoleFromToken(token);
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (role != null && !role.isBlank()) {
          authorities.add(new SimpleGrantedAuthority("ROLE_" + role.trim().toUpperCase()));
        }
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(
                String.valueOf(userId), null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
      }
    }

    filterChain.doFilter(request, response);
  }
}
