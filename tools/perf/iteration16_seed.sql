-- Iteration 16 performance seed data
-- Idempotent inserts for benchmark only.

INSERT INTO t_user(email, password_hash, nickname, avatar_url, status, role, failed_attempts, created_at, updated_at)
SELECT
  format('bench_user_%s@example.com', gs),
  'bench_hash',
  format('bench_user_%s', gs),
  NULL,
  CASE WHEN gs % 20 = 0 THEN 'DISABLED' ELSE 'ACTIVE' END,
  CASE WHEN gs % 25 = 0 THEN 'ADMIN' ELSE 'USER' END,
  0,
  NOW() - (gs || ' minutes')::interval,
  NOW() - (gs || ' minutes')::interval
FROM generate_series(1, 20000) gs
ON CONFLICT (email) DO NOTHING;

INSERT INTO t_trade_order(
  user_id, client_order_id, stock_code, stock_name, side, order_type, status,
  price, quantity, filled_quantity, filled_amount, commission, frozen_amount, version,
  created_at, updated_at
)
SELECT
  1,
  format('bench-order-%s', gs),
  'sh600519',
  'bench-stock',
  CASE WHEN gs % 2 = 0 THEN 'BUY' ELSE 'SELL' END,
  'LIMIT',
  CASE
    WHEN gs % 10 = 0 THEN 'CANCELLED'
    WHEN gs % 11 = 0 THEN 'EXPIRED'
    WHEN gs % 12 = 0 THEN 'REJECTED'
    WHEN gs % 3 = 0 THEN 'FILLED'
    ELSE 'PENDING'
  END,
  1000 + (gs % 500),
  100,
  CASE WHEN gs % 3 = 0 THEN 100 ELSE 0 END,
  CASE WHEN gs % 3 = 0 THEN 100000 + (gs % 50000) ELSE 0 END,
  5,
  CASE WHEN gs % 3 = 0 THEN 0 ELSE 100000 + (gs % 50000) END,
  0,
  NOW() - (gs || ' seconds')::interval,
  NOW() - (gs || ' seconds')::interval
FROM generate_series(1, 120000) gs
ON CONFLICT (client_order_id) DO NOTHING;

INSERT INTO t_trade_record(
  order_id, user_id, stock_code, stock_name, side,
  trade_price, trade_quantity, trade_amount, commission, traded_at, created_at
)
SELECT
  o.id,
  o.user_id,
  o.stock_code,
  o.stock_name,
  o.side,
  o.price,
  o.quantity,
  o.price * o.quantity,
  5,
  o.updated_at,
  o.updated_at
FROM t_trade_order o
WHERE o.client_order_id LIKE 'bench-order-%'
  AND o.status = 'FILLED'
  AND NOT EXISTS (
    SELECT 1 FROM t_trade_record t WHERE t.order_id = o.id
  )
LIMIT 40000;

INSERT INTO t_portfolio_position(
  user_id, stock_code, stock_name, total_quantity, available_quantity,
  frozen_quantity, cost_price, total_cost, frozen_until, version, created_at, updated_at
)
SELECT
  1,
  format('bench%s', lpad(gs::text, 6, '0')),
  format('bench-stock-%s', gs),
  CASE WHEN gs % 3 = 0 THEN 0 ELSE 100 + (gs % 1000) END,
  CASE WHEN gs % 3 = 0 THEN 0 ELSE 100 + (gs % 1000) END,
  0,
  10 + (gs % 90),
  1000 + (gs % 9000),
  NULL,
  0,
  NOW() - (gs || ' minutes')::interval,
  NOW() - (gs || ' minutes')::interval
FROM generate_series(1, 30000) gs
ON CONFLICT (user_id, stock_code) DO NOTHING;

ANALYZE t_user;
ANALYZE t_trade_order;
ANALYZE t_trade_record;
ANALYZE t_portfolio_position;
