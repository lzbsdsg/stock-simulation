import request, { unwrapResponse } from '@/api/request'
import type { WatchlistItem } from '@/types/watchlist'

export async function getWatchlist(): Promise<WatchlistItem[]> {
  return unwrapResponse<WatchlistItem[]>(request.get('/watchlist'))
}

export async function addWatchStock(stockCode: string): Promise<void> {
  await unwrapResponse<void>(request.post(`/watchlist/${stockCode}`))
}

export async function removeWatchStock(stockCode: string): Promise<void> {
  await unwrapResponse<void>(request.delete(`/watchlist/${stockCode}`))
}

export async function updateWatchSort(stockCodes: string[]): Promise<void> {
  await unwrapResponse<void>(request.put('/watchlist/sort', stockCodes))
}
