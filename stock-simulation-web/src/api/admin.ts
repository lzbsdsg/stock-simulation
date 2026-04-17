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
  }
}
