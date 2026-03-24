package com.lzbsdsg.stocksimulation.market.infrastructure.resilience;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class ProviderCircuitBreakerTest {

  @Test
  void should_allow_request_when_closed() {
    MutableClock clock = new MutableClock(Instant.parse("2026-03-24T00:00:00Z"));
    ProviderCircuitBreaker breaker = new ProviderCircuitBreaker(3, Duration.ofSeconds(30), clock);

    assertTrue(breaker.allowRequest());
    assertEquals(ProviderCircuitBreaker.State.CLOSED, breaker.getState());
  }

  @Test
  void should_open_after_three_failures() {
    MutableClock clock = new MutableClock(Instant.parse("2026-03-24T00:00:00Z"));
    ProviderCircuitBreaker breaker = new ProviderCircuitBreaker(3, Duration.ofSeconds(30), clock);

    breaker.onFailure();
    breaker.onFailure();
    breaker.onFailure();

    assertEquals(ProviderCircuitBreaker.State.OPEN, breaker.getState());
    assertFalse(breaker.allowRequest());
  }

  @Test
  void should_switch_to_half_open_after_timeout() {
    MutableClock clock = new MutableClock(Instant.parse("2026-03-24T00:00:00Z"));
    ProviderCircuitBreaker breaker = new ProviderCircuitBreaker(3, Duration.ofSeconds(30), clock);
    breaker.onFailure();
    breaker.onFailure();
    breaker.onFailure();

    clock.plusSeconds(31);

    assertTrue(breaker.allowRequest());
    assertEquals(ProviderCircuitBreaker.State.HALF_OPEN, breaker.getState());
  }

  @Test
  void should_recover_to_closed_after_half_open_success() {
    MutableClock clock = new MutableClock(Instant.parse("2026-03-24T00:00:00Z"));
    ProviderCircuitBreaker breaker = new ProviderCircuitBreaker(3, Duration.ofSeconds(30), clock);
    breaker.onFailure();
    breaker.onFailure();
    breaker.onFailure();
    clock.plusSeconds(31);
    breaker.allowRequest();

    breaker.onSuccess();

    assertEquals(ProviderCircuitBreaker.State.CLOSED, breaker.getState());
    assertTrue(breaker.allowRequest());
  }

  private static final class MutableClock extends Clock {
    private Instant instant;

    private MutableClock(Instant instant) {
      this.instant = instant;
    }

    @Override
    public ZoneId getZone() {
      return ZoneId.of("UTC");
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }

    private void plusSeconds(long seconds) {
      instant = instant.plusSeconds(seconds);
    }
  }
}
