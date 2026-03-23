-- =============================================
-- V20260213_002 — 用户账户表
-- =============================================

CREATE TABLE t_user_account (
    id                BIGSERIAL       PRIMARY KEY,
    user_id           BIGINT          NOT NULL UNIQUE REFERENCES t_user(id),
    initial_balance   NUMERIC(18,2)   NOT NULL DEFAULT 100000.00,
    available_balance NUMERIC(18,2)   NOT NULL DEFAULT 100000.00,
    frozen_balance    NUMERIC(18,2)   NOT NULL DEFAULT 0.00,
    version           INTEGER         NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_account_user_id ON t_user_account(user_id);

COMMENT ON TABLE  t_user_account IS '用户资金账户';
COMMENT ON COLUMN t_user_account.initial_balance IS '初始资金';
COMMENT ON COLUMN t_user_account.available_balance IS '可用资金';
COMMENT ON COLUMN t_user_account.frozen_balance IS '冻结资金';
COMMENT ON COLUMN t_user_account.version IS '乐观锁版本号';
