package com.lzbsdsg.stocksimulation.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * k6 压测旁路认证过滤器。
 *
 * <p>仅在显式开启并且请求携带正确旁路密钥时生效，用于压测环境下无 Token 访问受保护接口。
 */
@Component
public class K6BypassAuthenticationFilter extends OncePerRequestFilter {

  private static final String K6_BYPASS_HEADER = "X-K6-Bypass-Key";

  @Value("${app.security.k6-bypass.enabled:false}")
  private boolean enabled;

  @Value("${app.security.k6-bypass.key:}")
  private String bypassKey;

  @Value("${app.security.k6-bypass.user-id:1}")
  private long bypassUserId;

  @Value("${app.security.k6-bypass.role:USER}")
  private String bypassRole;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (!enabled || bypassKey == null || bypassKey.isBlank()) {
      filterChain.doFilter(request, response);
      return;
    }

    Authentication existing = SecurityContextHolder.getContext().getAuthentication();
    if (existing != null
        && !(existing instanceof AnonymousAuthenticationToken)
        && existing.isAuthenticated()) {
      filterChain.doFilter(request, response);
      return;
    }

    String requestBypassKey = request.getHeader(K6_BYPASS_HEADER);
    if (requestBypassKey == null
        || requestBypassKey.isBlank()
        || !bypassKey.equals(requestBypassKey)) {
      filterChain.doFilter(request, response);
      return;
    }

    String normalizedRole =
        (bypassRole == null || bypassRole.isBlank())
            ? "USER"
            : bypassRole.trim().toUpperCase(Locale.ROOT);
    List<GrantedAuthority> authorities = new ArrayList<>();
    authorities.add(new SimpleGrantedAuthority("ROLE_" + normalizedRole));

    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(String.valueOf(bypassUserId), null, authorities);
    SecurityContextHolder.getContext().setAuthentication(authentication);

    filterChain.doFilter(request, response);
  }
}
