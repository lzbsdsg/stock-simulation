-- =============================================
-- V20260325_001 — 委托订单归档表
-- =============================================

CREATE TABLE t_trade_order_archive (
    id               BIGSERIAL       PRIMARY KEY,
    order_id         BIGINT          NOT NULL UNIQUE,
    user_id          BIGINT          NOT NULL REFERENCES t_user(id),
    client_order_id  VARCHAR(64)     NOT NULL,
    stock_code       VARCHAR(20)     NOT NULL,
    stock_name       VARCHAR(64),
    side             VARCHAR(10)     NOT NULL,
    order_type       VARCHAR(10)     NOT NULL,
    status           VARCHAR(20)     NOT NULL,
    price            NUMERIC(18,4)   NOT NULL,
    quantity         INTEGER         NOT NULL,
    filled_quantity  INTEGER         NOT NULL,
    filled_amount    NUMERIC(18,2)   NOT NULL,
    commission       NUMERIC(18,2)   NOT NULL,
    frozen_amount    NUMERIC(18,2)   NOT NULL,
    version          INTEGER         NOT NULL,
    created_at       TIMESTAMPTZ     NOT NULL,
    updated_at       TIMESTAMPTZ     NOT NULL,
    archived_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_order_archive_user_created ON t_trade_order_archive(user_id, created_at DESC);
CREATE INDEX idx_order_archive_status ON t_trade_order_archive(status);
CREATE INDEX idx_order_archive_client_id ON t_trade_order_archive(client_order_id);

COMMENT ON TABLE t_trade_order_archive IS '委托订单归档表';
COMMENT ON COLUMN t_trade_order_archive.order_id IS '原 t_trade_order.id';
COMMENT ON COLUMN t_trade_order_archive.archived_at IS '归档时间';
