-- Iteration 16 SQL benchmark probes
-- Run before and after optimization indexes.

DO $$
DECLARE
  i integer;
  t0 timestamptz;
  ms numeric;
  q1 numeric := 0;
  q2 numeric := 0;
  q3 numeric := 0;
  q4 numeric := 0;
  q5 numeric := 0;
BEGIN
  FOR i IN 1..50 LOOP
    t0 := clock_timestamp();
    PERFORM id FROM t_user ORDER BY created_at DESC, id DESC LIMIT 100 OFFSET 5000;
    ms := EXTRACT(EPOCH FROM (clock_timestamp() - t0)) * 1000;
    q1 := q1 + ms;

    t0 := clock_timestamp();
    PERFORM count(*) FROM t_user WHERE role = 'ADMIN';
    ms := EXTRACT(EPOCH FROM (clock_timestamp() - t0)) * 1000;
    q2 := q2 + ms;

    t0 := clock_timestamp();
    PERFORM count(*) FROM t_trade_record WHERE traded_at >= NOW() - interval '1 day';
    ms := EXTRACT(EPOCH FROM (clock_timestamp() - t0)) * 1000;
    q3 := q3 + ms;

    t0 := clock_timestamp();
    PERFORM id
    FROM t_trade_order
    WHERE status IN ('CANCELLED', 'EXPIRED', 'REJECTED')
      AND updated_at < NOW() - interval '1 day'
    ORDER BY updated_at ASC, id ASC
    LIMIT 500;
    ms := EXTRACT(EPOCH FROM (clock_timestamp() - t0)) * 1000;
    q4 := q4 + ms;

    t0 := clock_timestamp();
    PERFORM count(*) FROM t_portfolio_position WHERE user_id = 1 AND total_quantity > 0;
    ms := EXTRACT(EPOCH FROM (clock_timestamp() - t0)) * 1000;
    q5 := q5 + ms;
  END LOOP;

  RAISE NOTICE 'Q1_USER_LIST_AVG_MS=%.3f', q1 / 50;
  RAISE NOTICE 'Q2_USER_ROLE_COUNT_AVG_MS=%.3f', q2 / 50;
  RAISE NOTICE 'Q3_TRADE_1D_COUNT_AVG_MS=%.3f', q3 / 50;
  RAISE NOTICE 'Q4_ARCHIVE_CANDIDATE_AVG_MS=%.3f', q4 / 50;
  RAISE NOTICE 'Q5_POSITION_POSITIVE_AVG_MS=%.3f', q5 / 50;
END$$;
