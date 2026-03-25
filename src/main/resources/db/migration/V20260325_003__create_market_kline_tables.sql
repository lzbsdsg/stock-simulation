-- =============================================
-- V20260325_003 — 历史日K持久化表
-- =============================================

CREATE TABLE IF NOT EXISTS t_market_kline_daily (
    id          BIGSERIAL       PRIMARY KEY,
    stock_code  VARCHAR(20)     NOT NULL,
    trade_date  DATE            NOT NULL,
    open_price  NUMERIC(20,4)   NOT NULL,
    close_price NUMERIC(20,4)   NOT NULL,
    high_price  NUMERIC(20,4)   NOT NULL,
    low_price   NUMERIC(20,4)   NOT NULL,
    volume      BIGINT          NOT NULL DEFAULT 0,
    amount      NUMERIC(24,4)   NOT NULL DEFAULT 0,
    source      VARCHAR(16)     NOT NULL DEFAULT 'EASTMONEY',
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_market_kline_daily UNIQUE (stock_code, trade_date)
);

CREATE INDEX IF NOT EXISTS idx_market_kline_daily_code_date
    ON t_market_kline_daily(stock_code, trade_date);

CREATE TABLE IF NOT EXISTS t_market_kline_sync_state (
    stock_code      VARCHAR(20) PRIMARY KEY,
    last_sync_date  DATE        NOT NULL,
    last_bar_date   DATE,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE t_market_kline_daily IS '股票历史日K数据（真实数据）';
COMMENT ON TABLE t_market_kline_sync_state IS '历史日K按股票的每日同步状态';
