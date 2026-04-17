-- Output benchmark as query result rows

CREATE OR REPLACE FUNCTION bench_iteration16(iterations integer)
RETURNS TABLE(metric text, avg_ms numeric)
LANGUAGE plpgsql
AS $$
DECLARE
  i integer;
  t0 timestamptz;
  q1 numeric := 0;
  q2 numeric := 0;
  q3 numeric := 0;
  q4 numeric := 0;
  q5 numeric := 0;
  probe_user_id bigint;
BEGIN
  SELECT id INTO probe_user_id FROM t_user ORDER BY id LIMIT 1;

  FOR i IN 1..iterations LOOP
    t0 := clock_timestamp();
    PERFORM id FROM t_user ORDER BY created_at DESC, id DESC LIMIT 100 OFFSET 5000;
    q1 := q1 + EXTRACT(EPOCH FROM (clock_timestamp() - t0)) * 1000;

    t0 := clock_timestamp();
    PERFORM count(*) FROM t_user WHERE role = 'ADMIN';
    q2 := q2 + EXTRACT(EPOCH FROM (clock_timestamp() - t0)) * 1000;

    t0 := clock_timestamp();
    PERFORM count(*) FROM t_trade_record WHERE traded_at >= NOW() - interval '1 day';
    q3 := q3 + EXTRACT(EPOCH FROM (clock_timestamp() - t0)) * 1000;

    t0 := clock_timestamp();
    PERFORM id
    FROM t_trade_order
    WHERE status IN ('CANCELLED', 'EXPIRED', 'REJECTED')
      AND updated_at < NOW() - interval '1 day'
    ORDER BY updated_at ASC, id ASC
    LIMIT 500;
    q4 := q4 + EXTRACT(EPOCH FROM (clock_timestamp() - t0)) * 1000;

    t0 := clock_timestamp();
    PERFORM count(*) FROM t_portfolio_position WHERE user_id = probe_user_id AND total_quantity > 0;
    q5 := q5 + EXTRACT(EPOCH FROM (clock_timestamp() - t0)) * 1000;
  END LOOP;

  RETURN QUERY VALUES
    ('Q1_USER_LIST', round(q1 / iterations, 3)),
    ('Q2_USER_ROLE_COUNT', round(q2 / iterations, 3)),
    ('Q3_TRADE_1D_COUNT', round(q3 / iterations, 3)),
    ('Q4_ARCHIVE_CANDIDATE', round(q4 / iterations, 3)),
    ('Q5_POSITION_POSITIVE', round(q5 / iterations, 3));
END;
$$;

SELECT * FROM bench_iteration16(50);
