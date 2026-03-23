-- =============================================
-- V20260213_003 — 股票信息表
-- =============================================

CREATE TABLE t_market_stock_info (
    id          BIGSERIAL       PRIMARY KEY,
    stock_code  VARCHAR(20)     NOT NULL UNIQUE,
    stock_name  VARCHAR(64)     NOT NULL,
    market      VARCHAR(10)     NOT NULL,                    -- SH / SZ
    board_type  VARCHAR(10)     NOT NULL DEFAULT 'MAIN',     -- MAIN / GEM / STAR / ST
    industry    VARCHAR(64),
    listed      BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_stock_info_code ON t_market_stock_info(stock_code);
CREATE INDEX idx_stock_info_name ON t_market_stock_info(stock_name);
CREATE INDEX idx_stock_info_market ON t_market_stock_info(market);

COMMENT ON TABLE  t_market_stock_info IS '股票基本信息表';
COMMENT ON COLUMN t_market_stock_info.board_type IS '板块: MAIN=主板, GEM=创业板, STAR=科创板, ST';
