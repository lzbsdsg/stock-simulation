package com.lzbsdsg.stocksimulation.config;

/**
 * 异步线程池配置。
 *
 * <p>线程池参数： - corePoolSize: 8 - maxPoolSize: 32 - queueCapacity: 500 - threadNamePrefix: "async-" -
 * rejectedExecutionHandler: CallerRunsPolicy
 *
 * <p>用途： - MQ 异步发送邮件 - 收盘快照分批处理 - 其他非阻塞异步任务
 */
public class AsyncConfig {
  // TODO: @Configuration @EnableAsync
  // TODO: @Bean("taskExecutor") ThreadPoolTaskExecutor
}
