package com.lzbsdsg.stocksimulation.trade.infrastructure.gateway;

import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 幂等性网关（Redis SETNX）
 *
 * <p>使用 clientOrderId 做幂等键，SETNX + TTL 5min。 防止重复下单。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyGateway {

  private final RedisTemplate<String, Object> redisTemplate;

  private static final String IDEMPOTENCY_KEY_PREFIX = "trade:idempotent:";
  private static final long TTL_MINUTES = 5;

  /**
   * 尝试获取幂等锁
   *
   * @param clientOrderId 客户端幂等键
   * @return true 表示首次请求（成功获取锁），false 表示重复请求
   */
  public boolean tryAcquire(String clientOrderId) {
    String key = IDEMPOTENCY_KEY_PREFIX + clientOrderId;
    Boolean result =
        redisTemplate.opsForValue().setIfAbsent(key, "1", TTL_MINUTES, TimeUnit.MINUTES);
    return Boolean.TRUE.equals(result);
  }

  /** 释放幂等锁（撤单等场景可能需要） */
  public void release(String clientOrderId) {
    String key = IDEMPOTENCY_KEY_PREFIX + clientOrderId;
    redisTemplate.delete(key);
  }
}
