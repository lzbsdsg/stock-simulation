export interface AdminDashboardStats {
  totalUsers: number
  activeUsers: number
  disabledUsers: number
  adminUsers: number
  todayNewUsers: number
  totalTradeCount: number
  todayTradeCount: number
  totalTradeAmount: number
  todayTradeAmount: number
  totalAvailableBalance: number
  tradeOrderCreatedTotal: number
  tradeOrderFilledTotal: number
  tradeMatchDurationP95Ms: number | null
  tradeMatchDurationP99Ms: number | null
  marketQuoteCacheHitL1Total: number
  marketQuoteCacheHitL2Total: number
  wsActiveConnections: number
  wsPushDroppedTotal: number
  dbPoolMasterActiveConnections: number
  dbPoolSlaveActiveConnections: number
}
