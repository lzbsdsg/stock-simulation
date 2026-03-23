import { defineStore } from 'pinia'
import type { RateLimitInfo } from '@/types/http'

export const useAppStore = defineStore('app', {
  state: () => ({
    lastRateLimit: {} as RateLimitInfo,
  }),
  actions: {
    updateRateLimit(info: RateLimitInfo) {
      this.lastRateLimit = info
    },
  },
})
