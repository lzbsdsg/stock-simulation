-- =============================================
-- V20260324_004 — 股票基础数据种子（首批）
-- =============================================

INSERT INTO t_market_stock_info (stock_code, stock_name, market, board_type, industry, listed)
VALUES
  ('sh600519', '贵州茅台', 'SH', 'MAIN', '白酒', TRUE),
  ('sz000001', '平安银行', 'SZ', 'MAIN', '银行', TRUE)
ON CONFLICT (stock_code) DO UPDATE SET
  stock_name = EXCLUDED.stock_name,
  market = EXCLUDED.market,
  board_type = EXCLUDED.board_type,
  industry = EXCLUDED.industry,
  listed = EXCLUDED.listed,
  updated_at = NOW();
