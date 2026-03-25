-- ============================================================
-- V20260325_002__add_portfolio_month_partitions.sql
-- 通过 PostgreSQL 继承 + 路由触发器实现按月分表（兼容既有非分区主表）
-- ============================================================

-- ----------------------------
-- fund_flow 月表初始化（上月~未来6个月）
-- ----------------------------
DO $$
DECLARE
    i INTEGER;
    base_month DATE := (date_trunc('month', now() AT TIME ZONE 'Asia/Shanghai')::date - INTERVAL '1 month')::date;
    month_start DATE;
    month_end DATE;
    partition_name TEXT;
BEGIN
    FOR i IN 0..7 LOOP
        month_start := (base_month + make_interval(months => i))::date;
        month_end := (base_month + make_interval(months => i + 1))::date;
        partition_name := format('t_portfolio_fund_flow_%s', to_char(month_start, 'YYYY_MM'));

        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS %I (
                CHECK (created_at >= %L::timestamptz AND created_at < %L::timestamptz)
            ) INHERITS (t_portfolio_fund_flow)',
            partition_name, month_start, month_end);
        EXECUTE format(
            'CREATE INDEX IF NOT EXISTS %I ON %I (user_id, created_at DESC)',
            partition_name || '_user_created_idx', partition_name);
    END LOOP;
END $$;

-- ----------------------------
-- asset_snapshot 月表初始化（上月~未来6个月）
-- ----------------------------
DO $$
DECLARE
    i INTEGER;
    base_month DATE := (date_trunc('month', now() AT TIME ZONE 'Asia/Shanghai')::date - INTERVAL '1 month')::date;
    month_start DATE;
    month_end DATE;
    partition_name TEXT;
BEGIN
    FOR i IN 0..7 LOOP
        month_start := (base_month + make_interval(months => i))::date;
        month_end := (base_month + make_interval(months => i + 1))::date;
        partition_name := format('t_portfolio_asset_snapshot_%s', to_char(month_start, 'YYYY_MM'));

        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS %I (
                CHECK (snapshot_date >= %L::date AND snapshot_date < %L::date)
            ) INHERITS (t_portfolio_asset_snapshot)',
            partition_name, month_start, month_end);
        EXECUTE format(
            'CREATE UNIQUE INDEX IF NOT EXISTS %I ON %I (user_id, snapshot_date)',
            partition_name || '_user_date_uq', partition_name);
        EXECUTE format(
            'CREATE INDEX IF NOT EXISTS %I ON %I (user_id, snapshot_date)',
            partition_name || '_user_date_idx', partition_name);
    END LOOP;
END $$;

-- ----------------------------
-- fund_flow 路由触发器
-- ----------------------------
CREATE OR REPLACE FUNCTION route_portfolio_fund_flow_partition()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    month_start DATE;
    month_end DATE;
    partition_name TEXT;
BEGIN
    IF NEW.created_at IS NULL THEN
        NEW.created_at := NOW();
    END IF;

    month_start := date_trunc('month', NEW.created_at AT TIME ZONE 'Asia/Shanghai')::date;
    month_end := (month_start + INTERVAL '1 month')::date;
    partition_name := format('t_portfolio_fund_flow_%s', to_char(month_start, 'YYYY_MM'));

    BEGIN
        EXECUTE format('INSERT INTO %I VALUES ($1.*)', partition_name) USING NEW;
    EXCEPTION WHEN undefined_table THEN
        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS %I (
                CHECK (created_at >= %L::timestamptz AND created_at < %L::timestamptz)
            ) INHERITS (t_portfolio_fund_flow)',
            partition_name, month_start, month_end);
        EXECUTE format(
            'CREATE INDEX IF NOT EXISTS %I ON %I (user_id, created_at DESC)',
            partition_name || '_user_created_idx', partition_name);
        EXECUTE format('INSERT INTO %I VALUES ($1.*)', partition_name) USING NEW;
    END;
    RETURN NULL;
END $$;

DROP TRIGGER IF EXISTS trg_route_portfolio_fund_flow_partition ON t_portfolio_fund_flow;
CREATE TRIGGER trg_route_portfolio_fund_flow_partition
BEFORE INSERT ON t_portfolio_fund_flow
FOR EACH ROW EXECUTE FUNCTION route_portfolio_fund_flow_partition();

-- ----------------------------
-- asset_snapshot 路由触发器
-- ----------------------------
CREATE OR REPLACE FUNCTION route_portfolio_asset_snapshot_partition()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    month_start DATE;
    month_end DATE;
    partition_name TEXT;
BEGIN
    IF NEW.snapshot_date IS NULL THEN
        NEW.snapshot_date := (NOW() AT TIME ZONE 'Asia/Shanghai')::date;
    END IF;
    IF NEW.created_at IS NULL THEN
        NEW.created_at := NOW();
    END IF;

    month_start := date_trunc('month', NEW.snapshot_date)::date;
    month_end := (month_start + INTERVAL '1 month')::date;
    partition_name := format('t_portfolio_asset_snapshot_%s', to_char(month_start, 'YYYY_MM'));

    BEGIN
        EXECUTE format('INSERT INTO %I VALUES ($1.*)', partition_name) USING NEW;
    EXCEPTION WHEN undefined_table THEN
        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS %I (
                CHECK (snapshot_date >= %L::date AND snapshot_date < %L::date)
            ) INHERITS (t_portfolio_asset_snapshot)',
            partition_name, month_start, month_end);
        EXECUTE format(
            'CREATE UNIQUE INDEX IF NOT EXISTS %I ON %I (user_id, snapshot_date)',
            partition_name || '_user_date_uq', partition_name);
        EXECUTE format(
            'CREATE INDEX IF NOT EXISTS %I ON %I (user_id, snapshot_date)',
            partition_name || '_user_date_idx', partition_name);
        EXECUTE format('INSERT INTO %I VALUES ($1.*)', partition_name) USING NEW;
    END;
    RETURN NULL;
END $$;

DROP TRIGGER IF EXISTS trg_route_portfolio_asset_snapshot_partition ON t_portfolio_asset_snapshot;
CREATE TRIGGER trg_route_portfolio_asset_snapshot_partition
BEFORE INSERT ON t_portfolio_asset_snapshot
FOR EACH ROW EXECUTE FUNCTION route_portfolio_asset_snapshot_partition();
