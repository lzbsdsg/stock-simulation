package com.lzbsdsg.stocksimulation.admin.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lzbsdsg.stocksimulation.auth.infrastructure.persistence.UserDO;
import com.lzbsdsg.stocksimulation.auth.infrastructure.persistence.UserMapper;
import com.lzbsdsg.stocksimulation.common.exception.BizException;
import com.lzbsdsg.stocksimulation.common.result.ErrorCode;
import com.lzbsdsg.stocksimulation.common.result.PageResult;
import com.lzbsdsg.stocksimulation.trade.infrastructure.persistence.OrderMapper;
import com.lzbsdsg.stocksimulation.portfolio.infrastructure.persistence.PositionDO;
import com.lzbsdsg.stocksimulation.portfolio.infrastructure.persistence.PositionMapper;
import com.lzbsdsg.stocksimulation.trade.infrastructure.persistence.TradeDO;
import com.lzbsdsg.stocksimulation.trade.infrastructure.persistence.TradeMapper;
import com.lzbsdsg.stocksimulation.user.infrastructure.persistence.AccountDO;
import com.lzbsdsg.stocksimulation.user.infrastructure.persistence.AccountMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 管理后台应用服务 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminApplicationService {

  private static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");
  private static final Set<String> ALLOWED_USER_STATUS = Set.of("ACTIVE", "DISABLED", "LOCKED");

  private final UserMapper userMapper;
  private final AccountMapper accountMapper;
  private final PositionMapper positionMapper;
  private final OrderMapper orderMapper;
  private final TradeMapper tradeMapper;
  private final MeterRegistry meterRegistry;

  public PageResult<Map<String, Object>> listUsers(int page, int size) {
    int safePage = normalizePage(page);
    int safeSize = normalizeSize(size);
    long total = userMapper.selectCount(null);
    if (total <= 0) {
      return new PageResult<>(List.of(), total, safePage, safeSize);
    }

    long offset = (long) (safePage - 1) * safeSize;
    List<UserDO> users =
        userMapper.selectList(
            new LambdaQueryWrapper<UserDO>()
                .orderByDesc(UserDO::getCreatedAt)
                .orderByDesc(UserDO::getId)
                .last("LIMIT " + safeSize + " OFFSET " + offset));

    if (users.isEmpty()) {
      return new PageResult<>(List.of(), total, safePage, safeSize);
    }

    List<Long> userIds = users.stream().map(UserDO::getId).toList();
    Map<Long, AccountDO> accountByUserId =
        accountMapper.selectList(new LambdaQueryWrapper<AccountDO>().in(AccountDO::getUserId, userIds)).stream()
            .collect(Collectors.toMap(AccountDO::getUserId, Function.identity()));

    List<Map<String, Object>> records = new ArrayList<>(users.size());
    for (UserDO user : users) {
      AccountDO account = accountByUserId.get(user.getId());
      BigDecimal initialBalance = account == null ? BigDecimal.ZERO : defaultZero(account.getInitialBalance());
      BigDecimal availableBalance = account == null ? BigDecimal.ZERO : defaultZero(account.getAvailableBalance());
      BigDecimal frozenBalance = account == null ? BigDecimal.ZERO : defaultZero(account.getFrozenBalance());
      long positionCount =
          positionMapper.selectCount(
              new LambdaQueryWrapper<PositionDO>()
                  .eq(PositionDO::getUserId, user.getId())
                  .gt(PositionDO::getTotalQuantity, 0));

      Map<String, Object> row = new LinkedHashMap<>();
      row.put("userId", user.getId());
      row.put("email", user.getEmail());
      row.put("nickname", user.getNickname());
      row.put("role", user.getRole());
      row.put("status", user.getStatus());
      row.put("createdAt", user.getCreatedAt());
      row.put("initialBalance", initialBalance);
      row.put("availableBalance", availableBalance);
      row.put("frozenBalance", frozenBalance);
      row.put("totalAssets", availableBalance.add(frozenBalance));
      row.put("positionCount", positionCount);
      records.add(row);
    }
    return new PageResult<>(records, total, safePage, safeSize);
  }

  public void toggleUserStatus(Long userId, String status) {
    UserDO user = userMapper.selectById(userId);
    if (user == null) {
      throw new BizException(ErrorCode.USER_NOT_FOUND);
    }

    String normalizedStatus = status == null ? "" : status.trim().toUpperCase();
    if (!ALLOWED_USER_STATUS.contains(normalizedStatus)) {
      throw new BizException(ErrorCode.BAD_REQUEST, "状态仅支持 ACTIVE / DISABLED / LOCKED");
    }

    if (normalizedStatus.equals(user.getStatus())) {
      return;
    }

    UserDO update = new UserDO();
    update.setId(userId);
    update.setStatus(normalizedStatus);
    userMapper.updateById(update);
  }

  public Map<String, Object> getDashboardStats() {
    long totalUsers = userMapper.selectCount(null);
    long activeUsers =
        userMapper.selectCount(new LambdaQueryWrapper<UserDO>().eq(UserDO::getStatus, "ACTIVE"));
    long disabledUsers =
        userMapper.selectCount(new LambdaQueryWrapper<UserDO>().eq(UserDO::getStatus, "DISABLED"));
    long adminUsers =
        userMapper.selectCount(new LambdaQueryWrapper<UserDO>().eq(UserDO::getRole, "ADMIN"));

    var todayStart = LocalDate.now(ZONE_SHANGHAI).atStartOfDay(ZONE_SHANGHAI).toInstant();
    long todayNewUsers =
        userMapper.selectCount(new LambdaQueryWrapper<UserDO>().ge(UserDO::getCreatedAt, todayStart));

    var todayTradeStart = LocalDate.now(ZONE_SHANGHAI).atStartOfDay(ZONE_SHANGHAI).toOffsetDateTime();
    long totalOrderCount = orderMapper.selectCount(null);
    long totalTradeCount = tradeMapper.selectCount(null);
    long todayTradeCount =
        tradeMapper.selectCount(new LambdaQueryWrapper<TradeDO>().ge(TradeDO::getTradedAt, todayTradeStart));
    BigDecimal totalTradeAmount = sumTradeAmount(null);
    BigDecimal todayTradeAmount = sumTradeAmount(todayTradeStart);
    BigDecimal totalAvailableBalance = sumAvailableBalance();

    Map<String, Object> stats = new LinkedHashMap<>();
    stats.put("totalUsers", totalUsers);
    stats.put("activeUsers", activeUsers);
    stats.put("disabledUsers", disabledUsers);
    stats.put("adminUsers", adminUsers);
    stats.put("todayNewUsers", todayNewUsers);
    stats.put("totalTradeCount", totalTradeCount);
    stats.put("todayTradeCount", todayTradeCount);
    stats.put("totalTradeAmount", totalTradeAmount);
    stats.put("todayTradeAmount", todayTradeAmount);
    stats.put("totalAvailableBalance", totalAvailableBalance);
    stats.put("tradeOrderCreatedTotal", metricCounterWithFallback("trade_order_created_total", totalOrderCount));
    stats.put("tradeOrderFilledTotal", metricCounterWithFallback("trade_order_filled_total", totalTradeCount));
    stats.put("tradeMatchDurationP95Ms", metricTimerPercentileMs("trade_match_duration_seconds", 0.95));
    stats.put("tradeMatchDurationP99Ms", metricTimerPercentileMs("trade_match_duration_seconds", 0.99));
    stats.put(
      "marketQuoteCacheHitL1Total",
      metricCounterWithTag("market_quote_cache_hit_total", "level", "L1"));
    stats.put(
      "marketQuoteCacheHitL2Total",
      metricCounterWithTag("market_quote_cache_hit_total", "level", "L2"));
    stats.put("wsActiveConnections", metricGauge("ws_active_connections"));
    stats.put("wsPushDroppedTotal", metricCounter("ws_push_dropped_total"));
    stats.put(
      "dbPoolMasterActiveConnections",
      metricGaugeWithTag("db_pool_active_connections", "source", "master"));
    stats.put(
      "dbPoolSlaveActiveConnections",
      metricGaugeWithTag("db_pool_active_connections", "source", "slave"));
    return stats;
  }

  public PageResult<Map<String, Object>> getLeaderboard(int page, int size) {
    int safePage = normalizePage(page);
    int safeSize = normalizeSize(size);

    List<AccountDO> accounts =
        accountMapper.selectList(
            new LambdaQueryWrapper<AccountDO>()
                .isNotNull(AccountDO::getUserId)
                .isNotNull(AccountDO::getInitialBalance));
    if (accounts.isEmpty()) {
      return new PageResult<>(List.of(), 0, safePage, safeSize);
    }

    List<Long> userIds = accounts.stream().map(AccountDO::getUserId).distinct().toList();
    Map<Long, UserDO> userById =
        userMapper.selectBatchIds(userIds).stream()
            .collect(Collectors.toMap(UserDO::getId, Function.identity(), (a, b) -> a));

    List<Map<String, Object>> ranking =
        accounts.stream()
            .map(
                account -> {
                  UserDO user = userById.get(account.getUserId());
                  BigDecimal initial = defaultZero(account.getInitialBalance());
                  BigDecimal available = defaultZero(account.getAvailableBalance());
                  BigDecimal frozen = defaultZero(account.getFrozenBalance());
                  BigDecimal totalAssets = available.add(frozen);
                  BigDecimal profit = totalAssets.subtract(initial);
                  BigDecimal profitRate =
                      initial.signum() <= 0
                          ? BigDecimal.ZERO
                          : profit.multiply(BigDecimal.valueOf(100)).divide(initial, 4, java.math.RoundingMode.HALF_UP);
                  Map<String, Object> row = new LinkedHashMap<>();
                  row.put("userId", account.getUserId());
                  row.put("email", user == null ? null : user.getEmail());
                  row.put("nickname", user == null ? null : user.getNickname());
                  row.put("initialBalance", initial);
                  row.put("totalAssets", totalAssets);
                  row.put("profit", profit);
                  row.put("profitRate", profitRate);
                  return row;
                })
            .sorted(
                Comparator.comparing(
                  (Map<String, Object> entry) ->
                    (BigDecimal) entry.getOrDefault("profitRate", BigDecimal.ZERO),
                        Comparator.reverseOrder())
                    .thenComparing(
                  (Map<String, Object> entry) ->
                    (BigDecimal) entry.getOrDefault("totalAssets", BigDecimal.ZERO),
                        Comparator.reverseOrder()))
            .toList();

    long total = ranking.size();
    long offset = (long) (safePage - 1) * safeSize;
    if (offset >= total) {
      return new PageResult<>(List.of(), total, safePage, safeSize);
    }
    int fromIndex = (int) offset;
    int toIndex = Math.min(fromIndex + safeSize, ranking.size());

    List<Map<String, Object>> pageRecords = new ArrayList<>(toIndex - fromIndex);
    for (int i = fromIndex; i < toIndex; i++) {
      Map<String, Object> row = new LinkedHashMap<>(ranking.get(i));
      row.put("rank", i + 1);
      pageRecords.add(row);
    }
    return new PageResult<>(pageRecords, total, safePage, safeSize);
  }

  private int normalizePage(int page) {
    return Math.max(page, 1);
  }

  private int normalizeSize(int size) {
    int safeSize = Math.max(size, 1);
    return Math.min(safeSize, 200);
  }

  private BigDecimal defaultZero(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  private BigDecimal sumTradeAmount(java.time.OffsetDateTime fromInclusive) {
    QueryWrapper<TradeDO> wrapper = new QueryWrapper<>();
    wrapper.select("COALESCE(SUM(trade_amount), 0)");
    if (fromInclusive != null) {
      wrapper.ge("traded_at", fromInclusive);
    }
    List<Object> values = tradeMapper.selectObjs(wrapper);
    if (values.isEmpty() || values.get(0) == null) {
      return BigDecimal.ZERO;
    }
    Object raw = values.get(0);
    if (raw instanceof BigDecimal bigDecimal) {
      return bigDecimal;
    }
    return new BigDecimal(String.valueOf(raw));
  }

  private BigDecimal sumAvailableBalance() {
    QueryWrapper<AccountDO> wrapper = new QueryWrapper<>();
    wrapper.select("COALESCE(SUM(available_balance), 0)");
    List<Object> values = accountMapper.selectObjs(wrapper);
    if (values.isEmpty() || values.get(0) == null) {
      return BigDecimal.ZERO;
    }
    Object raw = values.get(0);
    if (raw instanceof BigDecimal bigDecimal) {
      return bigDecimal;
    }
    return new BigDecimal(String.valueOf(raw));
  }

  private double metricCounter(String name) {
    var counter = meterRegistry.find(name).counter();
    return counter == null ? 0d : counter.count();
  }

  private double metricCounterWithTag(String name, String tagKey, String tagValue) {
    var counter = meterRegistry.find(name).tag(tagKey, tagValue).counter();
    return counter == null ? 0d : counter.count();
  }

  private double metricCounterWithFallback(String name, long fallbackValue) {
    double metricValue = metricCounter(name);
    if (metricValue > 0d) {
      return metricValue;
    }
    return Math.max(fallbackValue, 0L);
  }

  private double metricGauge(String name) {
    var gauge = meterRegistry.find(name).gauge();
    if (gauge == null) {
      return 0d;
    }
    double value = gauge.value();
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      return 0d;
    }
    return value;
  }

  private double metricGaugeWithTag(String name, String tagKey, String tagValue) {
    var gauge = meterRegistry.find(name).tag(tagKey, tagValue).gauge();
    if (gauge == null) {
      return 0d;
    }
    double value = gauge.value();
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      return 0d;
    }
    return value;
  }

  private Double metricTimerPercentileMs(String name, double percentile) {
    Timer timer = meterRegistry.find(name).timer();
    if (timer == null) {
      return null;
    }
    HistogramSnapshot snapshot = timer.takeSnapshot();
    for (ValueAtPercentile valueAtPercentile : snapshot.percentileValues()) {
      if (Math.abs(valueAtPercentile.percentile() - percentile) < 0.0001d) {
        double valueMs = valueAtPercentile.value(java.util.concurrent.TimeUnit.MILLISECONDS);
        return Double.isNaN(valueMs) || Double.isInfinite(valueMs) ? null : valueMs;
      }
    }
    return null;
  }
}
