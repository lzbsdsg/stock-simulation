-- =============================================
-- V20260417_001 — Iteration 16 热点查询索引优化
-- 目标：降低管理员统计、归档任务与时间窗口聚合查询耗时
-- =============================================

-- 管理员用户列表与当日新增统计
CREATE INDEX IF NOT EXISTS idx_user_created_id_desc ON t_user(created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_user_role ON t_user(role);

-- 成交按时间窗口统计（todayTradeCount / todayTradeAmount）
CREATE INDEX IF NOT EXISTS idx_trade_record_traded_at ON t_trade_record(traded_at DESC);

-- 历史归档任务候选扫描（status + updated_at + id）
CREATE INDEX IF NOT EXISTS idx_order_status_updated_id ON t_trade_order(status, updated_at ASC, id ASC);

-- 管理端用户持仓数量统计（按 user_id 且 total_quantity > 0）
CREATE INDEX IF NOT EXISTS idx_position_user_total_quantity
  ON t_portfolio_position(user_id, total_quantity);
