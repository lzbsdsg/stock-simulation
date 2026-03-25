import { defineStore } from 'pinia'
import type { CacheStatus, RateLimitInfo } from '@/types/http'

export const useAppStore = defineStore('app', {
  state: () => ({
    lastRateLimit: {} as RateLimitInfo,
    lastCacheStatus: 'UNKNOWN' as CacheStatus,
    lastMetaUpdateAt: 0,
  }),
  actions: {
    updateRateLimit(info: RateLimitInfo) {
      this.lastRateLimit = info
      this.lastMetaUpdateAt = Date.now()
    },
    updateCacheStatus(status: CacheStatus) {
      this.lastCacheStatus = status
      this.lastMetaUpdateAt = Date.now()
    },
  },
})
