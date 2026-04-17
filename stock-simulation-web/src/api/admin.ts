import request, { unwrapResponse } from '@/api/request'
import type { AdminDashboardStats } from '@/types/admin'

function toNumber(value: unknown): number {
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : 0
  }
  if (typeof value === 'string' && value.trim() !== '') {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : 0
  }
  return 0
}

function toNullableNumber(value: unknown): number | null {
  if (value === null || value === undefined || value === '') {
    return null
  }
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : null
  }
  if (typeof value === 'string' && value.trim() !== '') {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : null
  }
  return null
}

export async function getDashboardStats(): Promise<AdminDashboardStats> {
  const data = await unwrapResponse<Partial<AdminDashboardStats>>(request.get('/admin/dashboard/stats'))
  return {
    totalUsers: toNumber(data.totalUsers),
    activeUsers: toNumber(data.activeUsers),
    disabledUsers: toNumber(data.disabledUsers),
    adminUsers: toNumber(data.adminUsers),
    todayNewUsers: toNumber(data.todayNewUsers),
    totalTradeCount: toNumber(data.totalTradeCount),
    todayTradeCount: toNumber(data.todayTradeCount),
    totalTradeAmount: toNumber(data.totalTradeAmount),
    todayTradeAmount: toNumber(data.todayTradeAmount),
    totalAvailableBalance: toNumber(data.totalAvailableBalance),
    tradeOrderCreatedTotal: toNumber(data.tradeOrderCreatedTotal),
    tradeOrderFilledTotal: toNumber(data.tradeOrderFilledTotal),
    tradeMatchDurationP95Ms: toNullableNumber(data.tradeMatchDurationP95Ms),
    tradeMatchDurationP99Ms: toNullableNumber(data.tradeMatchDurationP99Ms),
    marketQuoteCacheHitL1Total: toNumber(data.marketQuoteCacheHitL1Total),
    marketQuoteCacheHitL2Total: toNumber(data.marketQuoteCacheHitL2Total),
    wsActiveConnections: toNumber(data.wsActiveConnections),
    wsPushDroppedTotal: toNumber(data.wsPushDroppedTotal),
    dbPoolMasterActiveConnections: toNumber(data.dbPoolMasterActiveConnections),
    dbPoolSlaveActiveConnections: toNumber(data.dbPoolSlaveActiveConnections),
  }
}
