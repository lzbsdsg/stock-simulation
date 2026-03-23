-- =============================================
-- V20260213_006 — 自选股表
-- =============================================

CREATE TABLE t_watchlist_item (
    id          BIGSERIAL       PRIMARY KEY,
    user_id     BIGINT          NOT NULL REFERENCES t_user(id),
    stock_code  VARCHAR(20)     NOT NULL,
    stock_name  VARCHAR(64),
    sort_order  INTEGER         NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, stock_code)
);

CREATE INDEX idx_watchlist_user_id ON t_watchlist_item(user_id);
CREATE INDEX idx_watchlist_user_sort ON t_watchlist_item(user_id, sort_order);

COMMENT ON TABLE t_watchlist_item IS '自选股表';
