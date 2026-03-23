-- =============================================
-- V20260213_004 — 委托订单表 & 成交记录表
-- =============================================

-- 委托订单表
CREATE TABLE t_trade_order (
    id               BIGSERIAL       PRIMARY KEY,
    user_id          BIGINT          NOT NULL REFERENCES t_user(id),
    client_order_id  VARCHAR(64)     NOT NULL UNIQUE,
    stock_code       VARCHAR(20)     NOT NULL,
    stock_name       VARCHAR(64),
    side             VARCHAR(10)     NOT NULL,                -- BUY / SELL
    order_type       VARCHAR(10)     NOT NULL DEFAULT 'LIMIT', -- LIMIT / MARKET
    status           VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    price            NUMERIC(18,4)   NOT NULL,
    quantity         INTEGER         NOT NULL,
    filled_quantity  INTEGER         NOT NULL DEFAULT 0,
    filled_amount    NUMERIC(18,2)   NOT NULL DEFAULT 0.00,
    commission       NUMERIC(18,2)   NOT NULL DEFAULT 0.00,
    frozen_amount    NUMERIC(18,2)   NOT NULL DEFAULT 0.00,
    version          INTEGER         NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_order_user_id ON t_trade_order(user_id);
CREATE INDEX idx_order_client_id ON t_trade_order(client_order_id);
CREATE INDEX idx_order_status ON t_trade_order(status);
CREATE INDEX idx_order_user_status ON t_trade_order(user_id, status);
CREATE INDEX idx_order_user_created ON t_trade_order(user_id, created_at DESC);

-- 成交记录表
CREATE TABLE t_trade_record (
    id              BIGSERIAL       PRIMARY KEY,
    order_id        BIGINT          NOT NULL REFERENCES t_trade_order(id),
    user_id         BIGINT          NOT NULL REFERENCES t_user(id),
    stock_code      VARCHAR(20)     NOT NULL,
    stock_name      VARCHAR(64),
    side            VARCHAR(10)     NOT NULL,
    trade_price     NUMERIC(18,4)   NOT NULL,
    trade_quantity  INTEGER         NOT NULL,
    trade_amount    NUMERIC(18,2)   NOT NULL,
    commission      NUMERIC(18,2)   NOT NULL DEFAULT 0.00,
    traded_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_trade_order_id ON t_trade_record(order_id);
CREATE INDEX idx_trade_user_id ON t_trade_record(user_id);
CREATE INDEX idx_trade_user_traded ON t_trade_record(user_id, traded_at DESC);

COMMENT ON TABLE  t_trade_order IS '委托订单表';
COMMENT ON TABLE  t_trade_record IS '成交记录表';
COMMENT ON COLUMN t_trade_order.client_order_id IS '客户端幂等键(UUID)';
COMMENT ON COLUMN t_trade_order.side IS '买卖方向: BUY/SELL';
COMMENT ON COLUMN t_trade_order.status IS '状态: PENDING/PARTIAL_FILLED/FILLED/CANCELLED/REJECTED/EXPIRED';
