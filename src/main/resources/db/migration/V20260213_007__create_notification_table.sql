-- =============================================
-- V20260213_007 — 通知消息表
-- =============================================

CREATE TABLE t_notification_message (
    id          BIGSERIAL       PRIMARY KEY,
    user_id     BIGINT          NOT NULL REFERENCES t_user(id),
    type        VARCHAR(30)     NOT NULL,  -- TRADE_FILLED / TRADE_CANCELLED / SYSTEM / RISK_ALERT
    title       VARCHAR(128)    NOT NULL,
    content     TEXT,
    read        BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notification_user_id ON t_notification_message(user_id);
CREATE INDEX idx_notification_user_read ON t_notification_message(user_id, read);
CREATE INDEX idx_notification_user_created ON t_notification_message(user_id, created_at DESC);

COMMENT ON TABLE  t_notification_message IS '消息通知表';
COMMENT ON COLUMN t_notification_message.type IS '类型: TRADE_FILLED/TRADE_CANCELLED/SYSTEM/RISK_ALERT';
