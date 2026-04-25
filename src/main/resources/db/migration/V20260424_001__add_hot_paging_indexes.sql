-- =============================================
-- V20260424_001 — 补充高频分页与今日查询索引
-- 目标：优化今日委托列表与资金流水分页查询
-- =============================================

-- trade today 查询（按 user_id + created_at + id 排序/分页）
CREATE INDEX IF NOT EXISTS idx_order_user_created_id_desc
  ON t_trade_order(user_id, created_at DESC, id DESC);

-- 资金流水分页（按 user_id + created_at + id 排序）
CREATE INDEX IF NOT EXISTS idx_fund_flow_user_created_id_desc
  ON t_portfolio_fund_flow(user_id, created_at DESC, id DESC);
