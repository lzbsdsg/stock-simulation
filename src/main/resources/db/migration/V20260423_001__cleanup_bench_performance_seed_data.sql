-- =============================================
-- V20260423_001 — 清理 bench 基准压测污染数据
-- =============================================

WITH bench_order_ids AS (
    SELECT id AS order_id
    FROM t_trade_order
    WHERE client_order_id LIKE 'bench-order-%'
    UNION
    SELECT order_id
    FROM t_trade_order_archive
    WHERE client_order_id LIKE 'bench-order-%'
)
DELETE FROM t_trade_record
WHERE order_id IN (SELECT order_id FROM bench_order_ids);

WITH bench_order_ids AS (
    SELECT id AS order_id
    FROM t_trade_order
    WHERE client_order_id LIKE 'bench-order-%'
    UNION
    SELECT order_id
    FROM t_trade_order_archive
    WHERE client_order_id LIKE 'bench-order-%'
)
DELETE FROM t_portfolio_fund_flow
WHERE order_id IN (SELECT order_id FROM bench_order_ids)
   OR remark ILIKE '%bench%';

DELETE FROM t_trade_order_archive
WHERE client_order_id LIKE 'bench-order-%';

DELETE FROM t_trade_order
WHERE client_order_id LIKE 'bench-order-%';

DELETE FROM t_portfolio_position
WHERE stock_code LIKE 'bench%'
   OR stock_name LIKE 'bench-stock-%';

ANALYZE t_trade_record;
ANALYZE t_portfolio_fund_flow;
ANALYZE t_trade_order_archive;
ANALYZE t_trade_order;
ANALYZE t_portfolio_position;
