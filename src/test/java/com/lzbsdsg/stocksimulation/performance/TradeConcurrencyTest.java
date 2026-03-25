package com.lzbsdsg.stocksimulation.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lzbsdsg.stocksimulation.common.exception.BizException;
import com.lzbsdsg.stocksimulation.user.application.AccountApplicationService;
import com.lzbsdsg.stocksimulation.user.domain.entity.Account;
import com.lzbsdsg.stocksimulation.user.domain.repository.AccountRepository;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

/** 交易并发安全测试。 */
public class TradeConcurrencyTest {

  @Test
  void should_serialize_same_account_freeze_and_allow_only_one_success_when_insufficient()
      throws InterruptedException {
    InMemoryAccountRepository repository = new InMemoryAccountRepository();
    repository.put(account(1L, "100", "100", "0", 0));
    AccountApplicationService service = new AccountApplicationService(repository);

    int threads = 10;
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threads);
    AtomicInteger success = new AtomicInteger();
    AtomicInteger insufficient = new AtomicInteger();

    for (int i = 0; i < threads; i++) {
      Thread thread =
          new Thread(
              () -> {
                await(start);
                try {
                  service.freezeBalance(1L, new BigDecimal("100"));
                  success.incrementAndGet();
                } catch (BizException ex) {
                  insufficient.incrementAndGet();
                } finally {
                  done.countDown();
                }
              });
      thread.start();
    }

    start.countDown();
    assertTrue(done.await(5, TimeUnit.SECONDS));

    assertEquals(1, success.get());
    assertEquals(9, insufficient.get());
    Account finalAccount = repository.findByUserId(1L).orElseThrow();
    assertEquals(new BigDecimal("0"), finalAccount.getAvailableBalance());
    assertEquals(new BigDecimal("100"), finalAccount.getFrozenBalance());
  }

  @Test
  void should_process_different_accounts_in_parallel() throws InterruptedException {
    InMemoryAccountRepository repository = new InMemoryAccountRepository();
    for (long userId = 1; userId <= 10; userId++) {
      repository.put(account(userId, "100", "100", "0", 0));
    }
    repository.setWriteDelayMillis(120);
    AccountApplicationService service = new AccountApplicationService(repository);

    int threads = 10;
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threads);
    AtomicInteger success = new AtomicInteger();

    long begin = System.currentTimeMillis();
    for (long userId = 1; userId <= threads; userId++) {
      final long currentUserId = userId;
      Thread thread =
          new Thread(
              () -> {
                await(start);
                service.freezeBalance(currentUserId, new BigDecimal("50"));
                success.incrementAndGet();
                done.countDown();
              });
      thread.start();
    }

    start.countDown();
    assertTrue(done.await(5, TimeUnit.SECONDS));
    long elapsed = System.currentTimeMillis() - begin;

    assertEquals(10, success.get());
    assertTrue(elapsed < 900);
  }

  @Test
  void should_retry_on_optimistic_lock_conflict_then_succeed() {
    InMemoryAccountRepository repository = new InMemoryAccountRepository();
    repository.put(account(2L, "500", "500", "0", 0));
    repository.setForcedConflictTimes(2L, 2);
    AccountApplicationService service = new AccountApplicationService(repository);

    service.freezeBalance(2L, new BigDecimal("100"));

    Account account = repository.findByUserId(2L).orElseThrow();
    assertEquals(new BigDecimal("400"), account.getAvailableBalance());
    assertEquals(new BigDecimal("100"), account.getFrozenBalance());
  }

  @Test
  void should_throw_optimistic_lock_exception_after_max_retries() {
    InMemoryAccountRepository repository = new InMemoryAccountRepository();
    repository.put(account(3L, "500", "500", "0", 0));
    repository.setForcedConflictTimes(3L, 10);
    AccountApplicationService service = new AccountApplicationService(repository);

    assertThrows(
        OptimisticLockingFailureException.class,
        () -> service.freezeBalance(3L, new BigDecimal("100")));
  }

  private Account account(
      Long userId, String initial, String available, String frozen, int version) {
    Account account = new Account();
    account.setUserId(userId);
    account.setInitialBalance(new BigDecimal(initial));
    account.setAvailableBalance(new BigDecimal(available));
    account.setFrozenBalance(new BigDecimal(frozen));
    account.setVersion(version);
    return account;
  }

  private void await(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }

  private static class InMemoryAccountRepository implements AccountRepository {

    private final Map<Long, Account> store = new ConcurrentHashMap<>();
    private final Map<Long, Object> userLocks = new ConcurrentHashMap<>();
    private final Map<Long, AtomicInteger> forcedConflicts = new ConcurrentHashMap<>();
    private volatile long writeDelayMillis = 0;

    void put(Account account) {
      store.put(account.getUserId(), copy(account));
      userLocks.putIfAbsent(account.getUserId(), new Object());
    }

    void setForcedConflictTimes(Long userId, int times) {
      forcedConflicts.put(userId, new AtomicInteger(times));
    }

    void setWriteDelayMillis(long writeDelayMillis) {
      this.writeDelayMillis = writeDelayMillis;
    }

    @Override
    public Optional<Account> findByUserId(Long userId) {
      Account account = store.get(userId);
      return Optional.ofNullable(account).map(this::copy);
    }

    @Override
    public Optional<Account> findByUserIdForUpdate(Long userId) {
      return findByUserId(userId);
    }

    @Override
    public Account save(Account account) {
      put(account);
      return copy(account);
    }

    @Override
    public boolean updateWithVersion(Account account) {
      Object lock = userLocks.computeIfAbsent(account.getUserId(), key -> new Object());
      synchronized (lock) {
        AtomicInteger conflicts = forcedConflicts.get(account.getUserId());
        if (conflicts != null && conflicts.getAndDecrement() > 0) {
          return false;
        }
        sleep(writeDelayMillis);
        Account current = store.get(account.getUserId());
        if (current == null || !current.getVersion().equals(account.getVersion())) {
          return false;
        }
        Account next = copy(account);
        next.setVersion(account.getVersion() + 1);
        store.put(account.getUserId(), next);
        return true;
      }
    }

    @Override
    public List<Long> findUserIdsAfter(Long lastUserId, int limit) {
      long cursor = lastUserId == null ? 0L : lastUserId;
      int safeLimit = limit <= 0 ? 500 : limit;
      return store.keySet().stream()
          .filter(userId -> userId > cursor)
          .sorted(Comparator.naturalOrder())
          .limit(safeLimit)
          .toList();
    }

    private Account copy(Account source) {
      Account account = new Account();
      account.setId(source.getId());
      account.setUserId(source.getUserId());
      account.setInitialBalance(source.getInitialBalance());
      account.setAvailableBalance(source.getAvailableBalance());
      account.setFrozenBalance(source.getFrozenBalance());
      account.setVersion(source.getVersion());
      return account;
    }

    private void sleep(long millis) {
      if (millis <= 0) {
        return;
      }
      try {
        Thread.sleep(millis);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      }
    }
  }
}
