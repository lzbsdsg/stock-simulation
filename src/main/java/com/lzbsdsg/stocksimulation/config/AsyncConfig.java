package com.lzbsdsg.stocksimulation.config;

import java.util.concurrent.ThreadPoolExecutor;
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

  @Bean("taskExecutor")
  public ThreadPoolTaskExecutor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(8);
    executor.setMaxPoolSize(32);
    executor.setQueueCapacity(500);
    executor.setThreadNamePrefix("async-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.initialize();
    return executor;
  }
}
