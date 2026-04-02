import { ref } from 'vue'
import { defineStore } from 'pinia'
import * as watchlistApi from '@/api/watchlist'
import type { WatchlistItem } from '@/types/watchlist'

export const useWatchlistStore = defineStore('watchlist', () => {
  const items = ref<WatchlistItem[]>([])
  const loading = ref(false)

  async function load(): Promise<void> {
    loading.value = true
    try {
      items.value = await watchlistApi.getWatchlist()
    } finally {
      loading.value = false
    }
  }

  async function add(stockCode: string): Promise<void> {
    await watchlistApi.addWatchStock(stockCode)
    await load()
  }

  async function remove(stockCode: string): Promise<void> {
    await watchlistApi.removeWatchStock(stockCode)
    await load()
  }

  async function updateSort(stockCodes: string[]): Promise<void> {
    await watchlistApi.updateWatchSort(stockCodes)
    await load()
  }

  return {
    items,
    loading,
    load,
    add,
    remove,
    updateSort,
  }
})
