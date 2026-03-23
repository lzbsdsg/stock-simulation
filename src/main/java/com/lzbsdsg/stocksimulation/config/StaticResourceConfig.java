package com.lzbsdsg.stocksimulation.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 静态资源映射配置。 */
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

  @Value("${avatar.storage.root-dir:uploads}")
  private String storageRootDir;

  @Value("${avatar.storage.public-prefix:/uploads}")
  private String publicPrefix;

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    Path root = Paths.get(storageRootDir).toAbsolutePath().normalize();
    String normalizedPrefix = normalizePublicPrefix(publicPrefix);
    registry
        .addResourceHandler(normalizedPrefix + "/**")
        .addResourceLocations("file:" + root.toString() + "/");
  }

  private String normalizePublicPrefix(String prefix) {
    String value = (prefix == null || prefix.isBlank()) ? "/uploads" : prefix;
    if (!value.startsWith("/")) {
      value = "/" + value;
    }
    if (value.endsWith("/")) {
      value = value.substring(0, value.length() - 1);
    }
    return value;
  }
}
