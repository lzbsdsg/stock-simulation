package com.lzbsdsg.stocksimulation.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 跨域配置 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

  @Value("${app.security.cors.allowed-origin-patterns:http://localhost:*,https://*.yourdomain.com}")
  private String allowedOriginPatterns;

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    String[] originPatterns =
        java.util.Arrays.stream(allowedOriginPatterns.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toArray(String[]::new);

    registry
        .addMapping("/api/**")
        .allowedOriginPatterns(originPatterns)
        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
        .allowedHeaders("*")
        .allowCredentials(true)
        .maxAge(3600);
  }
}
