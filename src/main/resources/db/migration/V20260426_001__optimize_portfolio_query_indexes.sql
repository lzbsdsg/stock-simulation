-- =============================================
-- V20260426_001 — 资产查询热点索引优化
-- 目标：优化持仓分页按 user_id + created_at DESC 排序的查询路径
-- =============================================

CREATE INDEX IF NOT EXISTS idx_position_user_created_id_desc
  ON t_portfolio_position(user_id, created_at DESC, id DESC);
