package com.lzbsdsg.stocksimulation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties
public class StockSimulationApplication {

  public static void main(String[] args) {
    SpringApplication.run(StockSimulationApplication.class, args);
  }
}
