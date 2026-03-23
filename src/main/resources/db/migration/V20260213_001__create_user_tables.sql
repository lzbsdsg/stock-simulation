-- =============================================
-- V20260213_001 — 用户表
-- =============================================

CREATE TABLE t_user (
    id          BIGSERIAL       PRIMARY KEY,
    email       VARCHAR(255)    NOT NULL UNIQUE,
    password_hash VARCHAR(255)  NOT NULL,
    nickname    VARCHAR(64)     NOT NULL,
    avatar_url  VARCHAR(512),
    status      VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE / LOCKED / DISABLED
    role        VARCHAR(20)     NOT NULL DEFAULT 'USER',     -- USER / ADMIN
    failed_attempts INTEGER     NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMPTZ
);

CREATE INDEX idx_user_email ON t_user(email);
CREATE INDEX idx_user_status ON t_user(status);

COMMENT ON TABLE  t_user IS '用户表';
COMMENT ON COLUMN t_user.status IS '账号状态: ACTIVE/LOCKED/DISABLED';
COMMENT ON COLUMN t_user.role IS '角色: USER/ADMIN';
