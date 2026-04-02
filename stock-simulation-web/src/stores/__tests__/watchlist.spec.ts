import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useWatchlistStore } from '@/stores/watchlist'

vi.mock('@/api/watchlist', () => ({
  getWatchlist: vi.fn(),
  addWatchStock: vi.fn(),
  removeWatchStock: vi.fn(),
  updateWatchSort: vi.fn(),
}))

import * as watchlistApi from '@/api/watchlist'

describe('useWatchlistStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('should load watchlist items', async () => {
    vi.mocked(watchlistApi.getWatchlist).mockResolvedValue([
      {
        stockCode: 'sh600519',
        stockName: '贵州茅台',
        currentPrice: 1888.88,
        changePercent: 1.23,
        sortOrder: 1,
      },
    ])

    const store = useWatchlistStore()
    await store.load()

    expect(store.items).toHaveLength(1)
    expect(store.items[0].stockCode).toBe('sh600519')
  })

  it('should call update sort api and reload', async () => {
    vi.mocked(watchlistApi.updateWatchSort).mockResolvedValue(undefined)
    vi.mocked(watchlistApi.getWatchlist)
      .mockResolvedValueOnce([
        {
          stockCode: 'sh600519',
          stockName: '贵州茅台',
          currentPrice: 1888.88,
          changePercent: 1.23,
          sortOrder: 1,
        },
      ])
      .mockResolvedValueOnce([
        {
          stockCode: 'sz000001',
          stockName: '平安银行',
          currentPrice: 12.34,
          changePercent: 0.2,
          sortOrder: 1,
        },
      ])

    const store = useWatchlistStore()
    await store.load()
    await store.updateSort(['sz000001'])

    expect(watchlistApi.updateWatchSort).toHaveBeenCalledWith(['sz000001'])
    expect(store.items[0].stockCode).toBe('sz000001')
  })
})
