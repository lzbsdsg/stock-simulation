package com.lzbsdsg.stocksimulation.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Compatibility mapping for OpenAPI trailing slash path. */
@Controller
public class OpenApiPathCompatController {

  @GetMapping("/v3/api-docs/")
  public String forwardApiDocsWithTrailingSlash() {
    return "forward:/v3/api-docs";
  }
}
