package com.lzbsdsg.stocksimulation.market.infrastructure.resilience;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** 提供方断路器：CLOSED -> OPEN -> HALF_OPEN。 */
public class ProviderCircuitBreaker {

  public enum State {
    CLOSED,
    OPEN,
    HALF_OPEN
  }

  private final int failureThreshold;
  private final Duration openDuration;
  private final Clock clock;

  private final AtomicInteger failureCount = new AtomicInteger(0);
  private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
  private final AtomicReference<Instant> openedAt = new AtomicReference<>(Instant.EPOCH);

  public ProviderCircuitBreaker(int failureThreshold, Duration openDuration) {
    this(failureThreshold, openDuration, Clock.systemUTC());
  }

  public ProviderCircuitBreaker(int failureThreshold, Duration openDuration, Clock clock) {
    if (failureThreshold <= 0) {
      throw new IllegalArgumentException("failureThreshold must be greater than 0");
    }
    this.failureThreshold = failureThreshold;
    this.openDuration = openDuration;
    this.clock = clock;
  }

  /** 是否允许当前请求通过。 */
  public boolean allowRequest() {
    State current = state.get();
    if (current == State.CLOSED || current == State.HALF_OPEN) {
      return true;
    }

    Instant openedAtValue = openedAt.get();
    Instant now = Instant.now(clock);
    if (!now.isBefore(openedAtValue.plus(openDuration))) {
      return state.compareAndSet(State.OPEN, State.HALF_OPEN);
    }
    return false;
  }

  /** 记录成功：HALF_OPEN 成功应恢复 CLOSED。 */
  public void onSuccess() {
    failureCount.set(0);
    state.set(State.CLOSED);
  }

  /** 记录失败：达到阈值后进入 OPEN。 */
  public void onFailure() {
    State current = state.get();
    if (current == State.HALF_OPEN) {
      toOpen();
      return;
    }

    int failures = failureCount.incrementAndGet();
    if (failures >= failureThreshold) {
      toOpen();
    }
  }

  public State getState() {
    return state.get();
  }

  private void toOpen() {
    state.set(State.OPEN);
    openedAt.set(Instant.now(clock));
    failureCount.set(0);
  }
}
