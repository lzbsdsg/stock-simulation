# 文档 C：测试方案

> 版本：1.0 | 日期：2026-02-13 | 状态：初稿

---

## 一、测试策略概述

### 1.1 测试金字塔

```
         ╱ ╲          E2E (Playwright)                少量(5-10条)
        ╱   ╲         性能 / 安全测试 (k6/OWASP ZAP)  专项
       ╱─────╲        API 接口测试 (REST Assured)      每接口≥4条
      ╱───────╲       集成测试 (Testcontainers)        每Repository≥3条
     ╱─────────╲      单元测试 (JUnit5 + Mockito)      Domain≥90% Service≥80%
    ╱───────────╲
```

### 1.2 质量目标

| 指标 | 目标值 |
|---|---|
| 单元测试覆盖率（domain） | ≥ 90% |
| 单元测试覆盖率（service） | ≥ 80% |
| 整体行覆盖率（JaCoCo） | ≥ 70% |
| API 接口覆盖率 | 100%（每接口≥ Happy + 401 + 400 + 业务异常） |
| E2E 核心流程覆盖 | ≥ 1 条完整链路 |
| 下单接口 P99 延迟 | < 200ms @ 100 并发 |
| 前端工具函数测试覆盖 | ≥ 70% |

### 1.3 工具清单

| 工具 | 用途 | 版本 |
|---|---|---|
| JUnit 5 | 单元测试框架 | 5.10+ |
| Mockito | Mock 框架 | 5.x |
| Testcontainers | 集成测试容器化(PG+Redis) | 1.19+ |
| REST Assured | HTTP API 测试 | 5.4+ |
| MockMvc | Spring MVC 测试 | Spring自带 |
| JaCoCo | 覆盖率统计 | 0.8.11+ |
| SonarQube | 代码质量分析 | Community |
| Spotless | 代码格式检查 | 2.40+ |
| Trivy | 容器镜像漏洞扫描 | Latest |
| OWASP ZAP | 安全扫描(可选) | Latest |
| k6 | 性能/压力测试 | 0.49+ |
| Vitest | 前端单元测试 | 1.x |
| Playwright | 前端 E2E 测试 | 1.40+ |
| Faker / JavaFaker | 测试数据生成 | — |

---

## 二、分层测试详细方案

### 2.1 单元测试 (Unit Tests)

#### 2.1.1 范围

- **Domain 层**：Entity、Domain Service、Value Object — 纯逻辑，无外部依赖
- **Application 层**：Application Service — Mock Repository/Gateway

#### 2.1.2 原则

- 每个测试方法 **只验证一个行为**
- 方法命名：`should_{预期行为}_when_{前置条件}`
- 使用 `@ExtendWith(MockitoExtension.class)` 注入 Mock
- Domain 层**不使用 Mock**（纯逻辑直接测试）
- 金额计算必须精确到分（`BigDecimal` 无浮点误差）

#### 2.1.3 Auth 模块测试

```java
// ===== OtpDomainServiceTest =====

@Test
void should_generate_6_digit_otp_code()

@Test
void should_return_true_when_otp_matches()

@Test
void should_return_false_when_otp_not_matches()

@Test
void should_return_false_when_otp_expired()

// ===== PasswordDomainServiceTest =====

@Test
void should_return_true_when_password_is_strong()

@Test
void should_return_false_when_password_too_short()

@Test
void should_return_false_when_password_missing_uppercase()

@Test
void should_return_false_when_password_missing_digit()

@Test
void should_lock_account_after_5_consecutive_failures()

@Test
void should_not_lock_account_with_less_than_5_failures()

@Test
void should_unlock_account_after_30_minutes()

// ===== AuthApplicationServiceTest (Mock) =====

@Test
void should_register_successfully_with_valid_otp()

@Test
void should_throw_when_otp_invalid_during_register()

@Test
void should_throw_when_email_already_registered()

@Test
void should_login_successfully_with_correct_password()

@Test
void should_increment_fail_count_on_wrong_password()

@Test
void should_throw_when_account_locked()

@Test
void should_refresh_token_and_rotate_old_refresh()

@Test
void should_throw_when_refresh_token_invalid()

@Test
void should_add_token_to_blacklist_on_logout()
```

#### 2.1.4 User/Account 模块测试

```java
// ===== AccountDomainServiceTest =====

@Test
void should_freeze_amount_when_sufficient_balance()

@Test
void should_throw_INSUFFICIENT_FUND_when_balance_not_enough()

@Test
void should_unfreeze_amount_and_restore_available()

@Test
void should_throw_when_unfreeze_exceeds_frozen()

@Test
void should_validate_balance_equation_after_every_operation()
// 余额恒等式: initialBalance == availableBalance + frozenBalance + 累计净支出

@Test
void should_create_account_with_valid_initial_balance_range()

@Test
void should_throw_when_initial_balance_below_minimum()

@Test
void should_throw_when_initial_balance_above_maximum()

// ===== UserControllerApiTest =====

@Test
void should_get_current_user_profile()

@Test
void should_update_current_user_profile()

@Test
void should_upload_avatar_successfully_with_multipart_file()

@Test
void should_reject_avatar_when_file_too_large_or_invalid_type()

@Test
void should_reject_avatar_upload_when_unauthenticated()

// ===== AvatarStorageServiceTest =====

@Test
void should_store_avatar_to_uploads_avatars_yyyymm_and_return_public_url()

@Test
void should_reject_non_image_file()

@Test
void should_reject_file_size_exceed_limit()

@Test
void should_fallback_extension_from_content_type_when_filename_missing_ext()
```

#### 2.1.5 Trade 模块测试

```java
// ===== OrderDomainServiceTest =====

@Test
void should_validate_order_quantity_is_multiple_of_100()

@Test
void should_throw_when_quantity_not_multiple_of_100()

@Test
void should_throw_when_quantity_is_zero_or_negative()

@Test
void should_validate_price_within_limit_up_down_for_normal_stock()

@Test
void should_validate_price_within_5_percent_for_ST_stock()

@Test
void should_validate_price_within_20_percent_for_GEM_stock()

@Test
void should_throw_when_outside_trading_hours()

@Test
void should_pass_during_morning_session()

@Test
void should_pass_during_afternoon_session()

// ===== MatchEngineTest =====

@Test
void should_match_buy_order_when_order_price_ge_market_price()

@Test
void should_not_match_buy_order_when_order_price_lt_market_price()

@Test
void should_match_sell_order_when_order_price_le_market_price()

@Test
void should_not_match_sell_order_when_order_price_gt_market_price()

@Test
void should_set_fill_price_to_market_price()

@Test
void should_return_not_matchable_for_suspended_stock()

// ===== FeeCalculatorTest =====

@Test
void should_calculate_buy_commission_correctly()

@Test
void should_apply_minimum_commission_5_yuan()

@Test
void should_calculate_sell_with_stamp_tax()

@Test
void should_not_charge_stamp_tax_for_buy()

@Test
void should_calculate_transfer_fee()

@Test
void should_sum_all_fees_correctly()

@Test
void should_handle_large_order_amount_precisely()
// 验证 BigDecimal 精度：100万 × 万三 = 300元

// ===== TradeApplicationServiceTest (Mock) =====

@Test
void should_place_buy_order_and_freeze_funds()

@Test
void should_reject_duplicate_order_by_client_order_id()

@Test
void should_place_sell_order_and_freeze_position()

@Test
void should_reject_sell_when_insufficient_available_quantity()

@Test
void should_reject_sell_within_T_plus_1_freeze()

@Test
void should_cancel_pending_buy_order_and_unfreeze_funds()

@Test
void should_cancel_pending_sell_order_and_unfreeze_position()

@Test
void should_reject_cancel_when_order_already_filled()

@Test
void should_match_and_fill_buy_order_in_single_transaction()

@Test
void should_match_and_fill_sell_order_and_credit_account()

@Test
void should_retry_on_optimistic_lock_conflict()

@Test
void should_fail_after_3_retries_on_persistent_conflict()
```

#### 2.1.6 Portfolio 模块测试

```java
// ===== PositionDomainServiceTest =====

@Test
void should_calculate_weighted_average_cost_price_on_buy()

@Test
void should_not_change_cost_price_on_sell()

@Test
void should_handle_first_buy_as_initial_cost_price()

@Test
void should_correctly_add_fees_to_cost()

@Test
void should_mark_new_buy_position_as_frozen_until_T_plus_1()

@Test
void should_reduce_position_to_zero_on_full_sell()

// ===== AssetSnapshotServiceTest =====

@Test
void should_generate_daily_snapshot_correctly()

@Test
void should_be_idempotent_for_same_date()

@Test
void should_calculate_profit_percent_correctly()
```

---

### 2.2 集成测试 (Integration Tests)

#### 2.2.1 范围

- **Repository 层**：验证 SQL 正确性，使用 Testcontainers PostgreSQL
- **Redis Gateway**：验证缓存读写，使用 Testcontainers Redis

#### 2.2.2 配置

```java
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
abstract class BaseIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
}
```

#### 2.2.3 测试用例

```java
// ===== AccountRepositoryIntegrationTest =====

@Test
void should_save_and_find_account_by_user_id()

@Test
void should_update_balance_with_optimistic_lock()

@Test
void should_throw_on_optimistic_lock_conflict()

@Test
void should_lock_row_with_select_for_update()

// ===== OrderRepositoryIntegrationTest =====

@Test
void should_save_and_query_orders_by_user_with_pagination()

@Test
void should_enforce_unique_client_order_id()

@Test
void should_query_pending_orders_for_matching()

@Test
void should_update_order_status_with_version()

// ===== PositionRepositoryIntegrationTest =====

@Test
void should_save_and_query_position_by_user_and_stock()

@Test
void should_enforce_unique_user_stock_constraint()

@Test
void should_update_position_with_optimistic_lock()

// ===== OtpRedisGatewayIntegrationTest =====

@Test
void should_store_and_verify_otp_in_redis()

@Test
void should_expire_otp_after_ttl()

@Test
void should_delete_otp_after_successful_verification()

// ===== MarketCacheGatewayIntegrationTest =====

@Test
void should_cache_quote_with_ttl()

@Test
void should_return_null_after_ttl_expires()

@Test
void should_persist_real_daily_kline_and_query_by_range()

@Test
void should_incrementally_sync_kline_only_when_stock_requested()

@Test
void should_keep_only_recent_three_years_kline_data()
```

---

### 2.3 API 接口测试 (Controller Tests)

#### 2.3.1 范围

- 每个 REST API 至少 4 条用例：Happy Path + 401 + 400 + 业务异常

#### 2.3.2 工具

- `MockMvc`：用于不启动完整服务器的快速测试
- `REST Assured`：用于启动完整 Spring Boot 上下文的端到端 API 测试

#### 2.3.3 测试用例

```java
// ===== AuthControllerApiTest =====

// POST /api/v1/auth/otp/send
@Test void should_send_otp_200()
@Test void should_reject_invalid_email_400()
@Test void should_rate_limit_otp_429()

// POST /api/v1/auth/register
@Test void should_register_successfully_200()
@Test void should_reject_weak_password_400()
@Test void should_reject_invalid_otp_400()
@Test void should_reject_duplicate_email_409()

// POST /api/v1/auth/login
@Test void should_login_successfully_200()
@Test void should_reject_wrong_password_401()
@Test void should_reject_locked_account_403()

// POST /api/v1/auth/refresh
@Test void should_refresh_token_200()
@Test void should_reject_expired_refresh_401()

// POST /api/v1/auth/logout
@Test void should_logout_200()
@Test void should_reject_without_token_401()

// ===== OrderControllerApiTest =====

// POST /api/v1/trade/orders
@Test void should_place_order_200()
@Test void should_reject_without_auth_401()
@Test void should_reject_invalid_quantity_400()
@Test void should_reject_insufficient_funds_TRADE_ORDER_INSUFFICIENT_FUND()
@Test void should_reject_outside_trading_hours_TRADE_ORDER_MARKET_CLOSED()
@Test void should_reject_duplicate_client_order_id_409()

// DELETE /api/v1/trade/orders/{id}
@Test void should_cancel_order_200()
@Test void should_reject_cancel_already_filled_409()
@Test void should_reject_cancel_not_own_order_403()

// GET /api/v1/trade/orders
@Test void should_list_orders_with_pagination_200()
@Test void should_return_empty_when_no_orders()

// ===== MarketControllerApiTest =====

// GET /api/v1/market/quote/{code}
@Test void should_return_quote_200()
@Test void should_reject_invalid_stock_code_400()
@Test void should_return_503_when_market_data_unavailable()

// ===== PortfolioControllerApiTest =====

// GET /api/v1/portfolio/overview
@Test void should_return_overview_200()
@Test void should_reject_without_auth_401()

// GET /api/v1/portfolio/positions
@Test void should_return_positions_with_realtime_profit()
@Test void should_return_empty_positions_for_new_user()

// ===== UserControllerApiTest =====

// GET /api/v1/user/me
@Test void should_get_current_user_200()
@Test void should_reject_without_auth_401()

// PUT /api/v1/user/me
@Test void should_update_profile_200()
@Test void should_reject_invalid_nickname_400()

// POST /api/v1/user/avatar
@Test void should_upload_avatar_200()
@Test void should_reject_avatar_invalid_type_400()
@Test void should_reject_avatar_too_large_400()
@Test void should_reject_avatar_without_auth_401()

// GET /uploads/**
@Test void should_access_uploaded_avatar_200()
@Test void should_return_404_when_avatar_not_exists()
```

---

### 2.4 全链路集成测试

```java
// ===== TradeFullFlowIntegrationTest =====

@Test
void should_complete_buy_flow_from_order_to_position() {
    // 1. 创建用户 + 初始资金100000
    // 2. 下单买入 sh600519 × 100股 × 1888.00
    // 3. 触发撮合（模拟市价1885.00 ≤ 委托价）
    // 4. 验证：Order.status = FILLED
    // 5. 验证：Position 存在，quantity=100, costPrice=1885.xx(含手续费)
    // 6. 验证：Account.available = 初始 - 成交额 - 手续费
    // 7. 验证：FundFlow 流水记录存在
}

@Test
void should_complete_sell_flow_after_T_plus_1() {
    // 1. 先完成买入（同上），手动跳过T+1
    // 2. 下单卖出 sh600519 × 100股 × 1900.00
    // 3. 撮合成交
    // 4. 验证：Position.quantity = 0
    // 5. 验证：Account 入账（卖出额 - 手续费）
}

@Test
void should_cancel_pending_order_and_restore_balance() {
    // 1. 下单买入
    // 2. 不触发撮合
    // 3. 撤单
    // 4. 验证：冻结金额恢复到可用
    // 5. 验证：Order.status = CANCELLED
}

// ===== AuthIntegrationTest =====

@Test
void should_complete_register_login_refresh_logout_flow() {
    // 1. 发送OTP → 注册 → 获取Token
    // 2. 用Token访问受保护接口 → 200
    // 3. 刷新Token → 获取新Token
    // 4. 旧Token仍可用（30min内）
    // 5. 登出 → 旧Token进入黑名单 → 401
}
```

---

## 三、性能测试方案

### 3.1 工具

- **k6**：脚本化(JavaScript)性能测试，CI 友好

### 3.2 测试场景

| 场景 | 并发 | 持续 | 通过标准 |
|---|---|---|---|
| 登录接口 | 100 VU | 2min | P99 < 500ms, Error < 1% |
| 获取行情 | 200 VU | 5min | P99 < 100ms, Error < 0.5% |
| 下单接口 | 100 VU | 5min | **P99 < 200ms**, Error < 1% |
| 批量行情 | 50 VU | 2min | P99 < 300ms |
| 混合场景 | 200 VU | 10min | P99 < 300ms, Error < 1% |

### 3.3 k6 脚本示例

```javascript
// k6/trade-order.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

export const options = {
  stages: [
    { duration: '30s', target: 50 },   // ramp up
    { duration: '5m',  target: 100 },  // sustained
    { duration: '30s', target: 0 },    // ramp down
  ],
  thresholds: {
    http_req_duration: ['p(99)<200'],   // P99 < 200ms
    http_req_failed: ['rate<0.01'],     // Error rate < 1%
  },
};

export default function () {
  const url = `${__ENV.BASE_URL}/api/v1/trade/orders`;
  const payload = JSON.stringify({
    clientOrderId: uuidv4(),
    stockCode: 'sh600519',
    side: 'BUY',
    orderType: 'LIMIT',
    price: 1888.00,
    quantity: 100,
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${__ENV.TOKEN}`,
    },
  };

  const res = http.post(url, payload, params);
  check(res, {
    'status is 200': (r) => r.status === 200,
    'has data': (r) => JSON.parse(r.body).data !== null,
  });

  sleep(1);
}
```

### 3.4 运行方式

```bash
# 本地运行
k6 run -e BASE_URL=http://localhost:8080 -e TOKEN=xxx k6/trade-order.js

# CI 中运行
k6 run --out json=k6-results.json k6/trade-order.js
```

### 3.5 可见股票集合实时性专项验证（2026-04）

#### 3.5.1 功能验证（可见集合上报）

```cmd
curl -X POST "http://localhost:8080/api/v1/market/visible-codes" ^
  -H "Authorization: Bearer <TOKEN>" ^
  -H "Content-Type: application/json" ^
  -d "[\"sh600519\",\"sz000001\",\"sh601318\"]"
```

通过标准：
- 返回 `code=200`。
- 1~2 秒内，以上股票可在 WS 订阅端收到更新。

#### 3.5.2 Redis 活跃池验证

```cmd
redis-cli ZREVRANGEBYSCORE market:active:quotes +inf -inf WITHSCORES LIMIT 0 20
```

通过标准：
- 可看到刚上报的股票代码。
- score 时间戳持续更新（心跳生效）。

#### 3.5.3 端到端实时性与延迟验证

使用现有脚本：

```cmd
k6 run -e WS_URL=ws://localhost:8080/ws/market/websocket -e WS_TOKEN=<TOKEN> -e TARGET_CODE=sh600519 k6/websocket-load-test.js
```

通过标准：
- `ws_push_latency_ms p(99) < 500`
- `ws_latency_samples_total > 0`

#### 3.5.4 可见集合切页场景验证

步骤：
1. 页面A上报股票集合A。
2. 页面B切换后上报股票集合B。
3. 观察 2 秒内 WS 推送从A集合切换为B集合。

通过标准：
- 切页后 2 秒内可见股票收到推送。
- 非可见集合推送明显下降（无持续无效推送）。

#### 3.5.5 性能目标（可见集合）

- 可见股票更新周期：1~2 秒。
- 行情接口 P95 < 50ms，P99 < 100ms。
- WS 推送延迟 P99 < 500ms。

#### 3.5.6 实时观测接口验证（新增）

```cmd
curl "http://localhost:8080/api/v1/market/realtime-metrics" ^
  -H "Authorization: Bearer <TOKEN>"
```

通过标准：
- 返回 `code=200`，且 `data` 字段完整。
- `activeCodeCount > 0`（已存在可见集合上报时）。
- `lastIngestCodeCount >= lastPublishedQuoteCount`。
- `ingestCycleLatency.count > 0`、`wsPushLatency.count > 0`。
- 压测期间 `wsQueuedTasks` 不持续线性增长（否则视为背压风险）。

建议观测阈值：
- `ingestCycleLatency.meanMs < 1000`
- `pubSubFanoutLatency.p99Ms < 200`（若有分位统计）
- `wsQueueLatency.p99Ms < 500`（若有分位统计）

---

## 四、安全测试方案

### 4.1 OWASP Top 10 检查

| # | 风险 | 测试方法 |
|---|---|---|
| A01 | 权限控制 | 越权访问测试（A用户操作B的订单） |
| A02 | 加密失败 | 密码BCrypt检查、Token不含敏感信息 |
| A03 | 注入 | SQL 注入测试（MyBatis参数化已防护） |
| A05 | 安全配置 | 检查生产环境关闭 Swagger/Actuator |
| A07 | 身份认证 | 暴力破解(5次锁定)、Token过期校验 |

### 4.2 安全测试用例

```java
// ===== SecurityTest =====

@Test
void should_lock_account_after_5_failed_login_attempts()

@Test
void should_reject_expired_access_token()

@Test
void should_reject_blacklisted_access_token()

@Test
void should_prevent_user_A_cancelling_user_B_order()

@Test
void should_prevent_sql_injection_in_stock_search()

@Test
void should_rate_limit_otp_send_per_email()

@Test
void should_rate_limit_otp_send_per_ip()

@Test
void should_not_reveal_user_existence_on_login_failure()
// 登录失败统一返回 "邮箱或密码错误"，不区分邮箱不存在/密码错误
```

### 4.3 容器漏洞扫描

```bash
# Trivy 扫描 Docker 镜像
trivy image stock-simulation:latest --severity CRITICAL,HIGH --exit-code 1
```

---

## 五、前端测试方案

### 5.1 单元测试 (Vitest)

```typescript
// ===== stores/auth.spec.ts =====

describe('useAuthStore', () => {
  it('should set token after login')
  it('should clear token after logout')
  it('should compute isLoggedIn correctly')
})

// ===== utils/format.spec.ts =====

describe('formatMoney', () => {
  it('should format 1000 to 1,000.00')
  it('should format negative as -500.00')
  it('should handle zero')
  it('should handle large numbers')
})

describe('formatPercent', () => {
  it('should format 0.1234 to +12.34%')
  it('should format -0.05 to -5.00%')
  it('should show 0.00% for zero')
})

// ===== utils/validators.spec.ts =====

describe('validateEmail', () => {
  it('should accept valid email')
  it('should reject invalid email')
})

describe('validatePassword', () => {
  it('should accept strong password')
  it('should reject short password')
  it('should reject password without uppercase')
})
```

### 5.2 E2E 测试 (Playwright)

```typescript
// ===== tests/e2e/trade-flow.spec.ts =====

test.describe('核心交易流程', () => {
  test('注册 → 登录 → 行情查看 → 下单 → 查看持仓', async ({ page }) => {
    // 1. 注册新用户（Mock OTP）
    await page.goto('/register');
    await page.fill('[data-testid=email]', 'test@example.com');
    // ... 填写注册表单
    await page.click('[data-testid=register-btn]');

    // 2. 登录
    await page.goto('/login');
    await page.fill('[data-testid=email]', 'test@example.com');
    await page.fill('[data-testid=password]', 'Test1234');
    await page.click('[data-testid=login-btn]');
    await expect(page).toHaveURL('/dashboard');

    // 3. 搜索股票
    await page.fill('[data-testid=stock-search]', '600519');
    await page.click('[data-testid=search-result-0]');

    // 4. 下单买入
    await page.fill('[data-testid=order-price]', '1888');
    await page.fill('[data-testid=order-quantity]', '100');
    await page.click('[data-testid=buy-btn]');
    await expect(page.locator('[data-testid=order-success-toast]')).toBeVisible();

    // 5. 查看持仓
    await page.click('[data-testid=nav-portfolio]');
    await expect(page.locator('[data-testid=position-row-0]')).toContainText('600519');
  });
});
```

---

## 六、CI 质量门禁配置

### 6.1 GitHub Actions CI Pipeline

```yaml
# .github/workflows/ci.yml
name: CI
on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main, develop]

jobs:
  test:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:16-alpine
        env:
          POSTGRES_DB: stock_test
          POSTGRES_USER: test
          POSTGRES_PASSWORD: test
        ports: ['5432:5432']
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
      redis:
        image: redis:7-alpine
        ports: ['6379:6379']

    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: maven

      # 格式检查
      - name: Spotless Check
        run: mvn spotless:check

      # 单元测试 + 集成测试
      - name: Run Tests
        run: mvn verify -Dspring.profiles.active=test
        env:
          SPRING_DATASOURCE_URL: jdbc:postgresql://localhost:5432/stock_test
          SPRING_DATASOURCE_USERNAME: test
          SPRING_DATASOURCE_PASSWORD: test
          SPRING_DATA_REDIS_HOST: localhost

      # 覆盖率检查
      - name: JaCoCo Coverage Check
        run: |
          COVERAGE=$(mvn jacoco:report -q && cat target/site/jacoco/index.html | grep -oP 'Total.*?(\d+)%' | grep -oP '\d+' | head -1)
          echo "Coverage: ${COVERAGE}%"
          if [ "$COVERAGE" -lt 70 ]; then
            echo "::error::Coverage ${COVERAGE}% is below 70% threshold"
            exit 1
          fi

      # 构建 Docker 镜像
      - name: Build Docker Image
        run: docker build -t stock-simulation:test .

      # 容器漏洞扫描
      - name: Trivy Scan
        uses: aquasecurity/trivy-action@master
        with:
          image-ref: stock-simulation:test
          severity: CRITICAL
          exit-code: 1

  frontend-test:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: stock-simulation-web

    steps:
      - uses: actions/checkout@v4

      - uses: pnpm/action-setup@v2
        with:
          version: 8

      - uses: actions/setup-node@v4
        with:
          node-version: 20
          cache: 'pnpm'
          cache-dependency-path: stock-simulation-web/pnpm-lock.yaml

      - run: pnpm install

      # ESLint + TypeScript 检查
      - name: Lint
        run: pnpm lint

      # 前端单元测试
      - name: Vitest
        run: pnpm test:unit --coverage

      # 前端构建
      - name: Build
        run: pnpm build
```

### 6.2 质量门禁汇总

| 门禁项 | 工具 | 阈值 | 阻断级别 |
|---|---|---|---|
| 单测全部通过 | JUnit 5 | 0 failures | **BLOCK** |
| JaCoCo 行覆盖率 | JaCoCo | ≥ 70% | **BLOCK** |
| Spotless 格式 | Spotless | 0 violations | **BLOCK** |
| Docker 构建 | Docker | Success | **BLOCK** |
| Trivy 漏洞 | Trivy | 0 Critical | **BLOCK** |
| ESLint | ESLint | 0 errors | **BLOCK** |
| TypeScript 编译 | tsc | 0 errors | **BLOCK** |
| Vitest 前端单测 | Vitest | 0 failures | **BLOCK** |
| 前端构建 | Vite | Success | **BLOCK** |
| SonarQube | SonarQube | 0 Critical/Blocker | WARN |

---

## 七、测试分阶段执行计划

### Phase 1（Week 1-4）：Auth + Account

| 测试类型 | 测试类 | 数量 |
|---|---|---|
| 单元测试 | OtpDomainServiceTest | 4 |
| 单元测试 | PasswordDomainServiceTest | 6 |
| 单元测试 | AccountDomainServiceTest | 8 |
| 单元测试 | AuthApplicationServiceTest | 9 |
| 集成测试 | AccountRepositoryIntegrationTest | 4 |
| 集成测试 | OtpRedisGatewayIntegrationTest | 3 |
| API测试 | AuthControllerApiTest | 11 |
| 全链路 | AuthIntegrationTest | 1 |
| **小计** | | **~46 条** |

### Phase 2（Week 5-8）：Market + Trade

| 测试类型 | 测试类 | 数量 |
|---|---|---|
| 单元测试 | OrderDomainServiceTest | 9 |
| 单元测试 | MatchEngineTest | 6 |
| 单元测试 | FeeCalculatorTest | 7 |
| 单元测试 | TradeApplicationServiceTest | 12 |
| 单元测试 | MarketDataFacadeTest | 5 |
| 集成测试 | OrderRepositoryIntegrationTest | 4 |
| 集成测试 | MarketCacheGatewayIntegrationTest | 3 |
| API测试 | OrderControllerApiTest | 10 |
| API测试 | MarketControllerApiTest | 4 |
| 全链路 | TradeFullFlowIntegrationTest | 3 |
| **小计** | | **~63 条** |

### Phase 3（Week 9-12）：Portfolio + WS + E2E

| 测试类型 | 测试类 | 数量 |
|---|---|---|
| 单元测试 | PositionDomainServiceTest | 6 |
| 单元测试 | AssetSnapshotServiceTest | 3 |
| 集成测试 | PositionRepositoryIntegrationTest | 3 |
| API测试 | PortfolioControllerApiTest | 4 |
| 前端单测 | auth.spec.ts | 3 |
| 前端单测 | format.spec.ts | 8 |
| 前端单测 | validators.spec.ts | 5 |
| E2E | trade-flow.spec.ts | 1 |
| **小计** | | **~33 条** |

### Phase 4（Week 13-14）：性能 + 安全

| 测试类型 | 测试类 | 数量 |
|---|---|---|
| 性能测试 | k6 场景 | 5 |
| 安全测试 | SecurityTest | 8 |
| 安全扫描 | Trivy | 1 |
| **小计** | | **~14 条** |

### 总计

| 类别 | 数量 |
|---|---|
| 后端单元测试 | ~75 条 |
| 集成测试 | ~17 条 |
| API 接口测试 | ~29 条 |
| 全链路测试 | ~4 条 |
| 性能测试场景 | ~5 条 |
| 安全测试 | ~9 条 |
| 前端单测 | ~16 条 |
| E2E 测试 | ~1 条 |
| **总计** | **~156 条** |

---

## 八、测试环境配置

### 8.1 application-test.yml

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/stock_test
    username: test
    password: test
  data:
    redis:
      host: localhost
      port: 6379
  flyway:
    enabled: true
  rabbitmq:
    host: localhost
    port: 5672

# 行情使用 Mock Provider
market:
  provider:
    primary: mock

# 日志级别
logging:
  level:
    com.lzbsdsg.stocksimulation: DEBUG
    org.springframework.security: DEBUG
```

### 8.2 测试目录结构

```
src/test/
├── java/com/lzbsdsg/stocksimulation/
│   ├── BaseIntegrationTest.java         # Testcontainers 基础类
│   ├── auth/
│   │   ├── domain/service/
│   │   │   ├── OtpDomainServiceTest.java
│   │   │   └── PasswordDomainServiceTest.java
│   │   ├── application/
│   │   │   └── AuthApplicationServiceTest.java
│   │   └── controller/
│   │       └── AuthControllerApiTest.java
│   ├── user/
│   │   ├── domain/service/
│   │   │   └── AccountDomainServiceTest.java
│   │   └── infrastructure/
│   │       └── AccountRepositoryIntegrationTest.java
│   ├── market/
│   │   ├── domain/service/
│   │   │   └── MarketDataFacadeTest.java
│   │   └── infrastructure/
│   │       ├── SinaMarketDataAdapterTest.java
│   │       └── MarketCacheGatewayIntegrationTest.java
│   ├── trade/
│   │   ├── domain/service/
│   │   │   ├── OrderDomainServiceTest.java
│   │   │   ├── MatchEngineTest.java
│   │   │   └── FeeCalculatorTest.java
│   │   ├── application/
│   │   │   └── TradeApplicationServiceTest.java
│   │   └── controller/
│   │       └── OrderControllerApiTest.java
│   ├── portfolio/
│   │   └── domain/service/
│   │       ├── PositionDomainServiceTest.java
│   │       └── AssetSnapshotServiceTest.java
│   ├── integration/
│   │   ├── TradeFullFlowIntegrationTest.java
│   │   └── AuthIntegrationTest.java
│   └── security/
│       └── SecurityTest.java
├── resources/
│   └── application-test.yml
└── k6/
    ├── login.js
    ├── market-quote.js
    ├── trade-order.js
    ├── batch-quotes.js
    └── mixed-scenario.js
```
