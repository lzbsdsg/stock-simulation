-- =============================================
-- V20260213_005 — 持仓表 & 资金流水表 & 资产快照表
-- =============================================

-- 持仓表
CREATE TABLE t_portfolio_position (
    id                BIGSERIAL       PRIMARY KEY,
    user_id           BIGINT          NOT NULL REFERENCES t_user(id),
    stock_code        VARCHAR(20)     NOT NULL,
    stock_name        VARCHAR(64),
    total_quantity    INTEGER         NOT NULL DEFAULT 0,
    available_quantity INTEGER        NOT NULL DEFAULT 0,
    frozen_quantity   INTEGER         NOT NULL DEFAULT 0,
    cost_price        NUMERIC(18,4)   NOT NULL DEFAULT 0.0000,
    total_cost        NUMERIC(18,2)   NOT NULL DEFAULT 0.00,
    frozen_until      DATE,
    version           INTEGER         NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, stock_code)
);

CREATE INDEX idx_position_user_id ON t_portfolio_position(user_id);
CREATE INDEX idx_position_user_stock ON t_portfolio_position(user_id, stock_code);

-- 资金流水表
CREATE TABLE t_portfolio_fund_flow (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         BIGINT          NOT NULL REFERENCES t_user(id),
    flow_type       VARCHAR(20)     NOT NULL,  -- INITIAL/TRADE_BUY/TRADE_SELL/COMMISSION/FREEZE/UNFREEZE
    amount          NUMERIC(18,2)   NOT NULL,
    balance_after   NUMERIC(18,2)   NOT NULL,
    order_id        BIGINT,
    remark          VARCHAR(255),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_fund_flow_user_id ON t_portfolio_fund_flow(user_id);
CREATE INDEX idx_fund_flow_user_created ON t_portfolio_fund_flow(user_id, created_at DESC);

-- 资产快照表
CREATE TABLE t_portfolio_asset_snapshot (
    id                    BIGSERIAL       PRIMARY KEY,
    user_id               BIGINT          NOT NULL REFERENCES t_user(id),
    snapshot_date         DATE            NOT NULL,
    total_assets          NUMERIC(18,2)   NOT NULL,
    available_balance     NUMERIC(18,2)   NOT NULL,
    market_value          NUMERIC(18,2)   NOT NULL DEFAULT 0.00,
    daily_profit          NUMERIC(18,2)   NOT NULL DEFAULT 0.00,
    cumulative_profit_rate NUMERIC(10,4)  NOT NULL DEFAULT 0.0000,
    created_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, snapshot_date)
);

CREATE INDEX idx_snapshot_user_date ON t_portfolio_asset_snapshot(user_id, snapshot_date);

COMMENT ON TABLE  t_portfolio_position IS '持仓表';
COMMENT ON TABLE  t_portfolio_fund_flow IS '资金流水表';
COMMENT ON TABLE  t_portfolio_asset_snapshot IS '资产快照表（每日收盘记录）';
COMMENT ON COLUMN t_portfolio_position.frozen_until IS 'T+1冻结截止日期';
