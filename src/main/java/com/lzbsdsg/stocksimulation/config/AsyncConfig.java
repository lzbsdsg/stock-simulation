package com.lzbsdsg.stocksimulation.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 异步线程池配置。
 *
 * <p>线程池参数： - corePoolSize: 8 - maxPoolSize: 32 - queueCapacity: 500 - threadNamePrefix: "async-" -
 * rejectedExecutionHandler: CallerRunsPolicy
 *
 * <p>用途： - MQ 异步发送邮件 - 收盘快照分批处理 - 其他非阻塞异步任务
 */
@Configuration
@EnableAsync
public class AsyncConfig {

  @Value("${app.async.core-pool-size:8}")
  private int corePoolSize;

  @Value("${app.async.max-pool-size:32}")
  private int maxPoolSize;

  @Value("${app.async.queue-capacity:500}")
  private int queueCapacity;

  @Value("${app.async.keep-alive-seconds:60}")
  private int keepAliveSeconds;

  @Bean("taskExecutor")
  public ThreadPoolTaskExecutor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(Math.max(1, corePoolSize));
    executor.setMaxPoolSize(Math.max(Math.max(1, corePoolSize), maxPoolSize));
    executor.setQueueCapacity(Math.max(100, queueCapacity));
    executor.setKeepAliveSeconds(Math.max(10, keepAliveSeconds));
    executor.setThreadNamePrefix("async-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.initialize();
    return executor;
  }
}
