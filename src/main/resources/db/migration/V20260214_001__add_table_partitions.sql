-- ============================================================
-- V20260214_001__add_table_partitions.sql
-- 为高频写入表添加按月范围分区
-- ============================================================

-- 1. t_portfolio_fund_flow — 资金流水按月分区
-- 注意：需要先重建表为分区表（首次迁移时）
-- ALTER TABLE t_portfolio_fund_flow RENAME TO t_portfolio_fund_flow_old;
-- CREATE TABLE t_portfolio_fund_flow (LIKE t_portfolio_fund_flow_old INCLUDING ALL)
--   PARTITION BY RANGE (created_at);
-- INSERT INTO t_portfolio_fund_flow SELECT * FROM t_portfolio_fund_flow_old;

-- 创建未来 6 个月分区（后续由定时任务自动提前创建下月分区）
-- CREATE TABLE t_portfolio_fund_flow_2026_01 PARTITION OF t_portfolio_fund_flow
--   FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');
-- CREATE TABLE t_portfolio_fund_flow_2026_02 PARTITION OF t_portfolio_fund_flow
--   FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');
-- ... 以此类推

-- 2. t_portfolio_asset_snapshot — 资产快照按月分区
-- CREATE TABLE t_portfolio_asset_snapshot (... ) PARTITION BY RANGE (snapshot_date);

-- 3. t_trade_deal — 成交记录按月分区
-- CREATE TABLE t_trade_deal (... ) PARTITION BY RANGE (traded_at);

-- ============================================================
-- 分区自动维护定时任务 SQL（cron_job 或 pg_cron 扩展）
-- ============================================================
-- SELECT cron.schedule('create-partitions', '0 0 25 * *',
--   $$ SELECT create_next_month_partitions() $$);

-- TODO: 实现 create_next_month_partitions() 函数
-- 该函数检查并提前创建下月分区，避免写入失败
