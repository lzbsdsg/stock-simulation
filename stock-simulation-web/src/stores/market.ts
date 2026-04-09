import { computed, ref } from 'vue'
import dayjs from 'dayjs'
import { defineStore } from 'pinia'
import * as marketApi from '@/api/market'
import { useWebSocket } from '@/composables/useWebSocket'
import { useAuthStore } from '@/stores/auth'
import type { KLinePeriod, KLinePoint, Quote } from '@/types/market'

const DEFAULT_CODES = ['sh600519', 'sz000001', 'sh601318', 'sh600036']

function normalizeCode(stockCode: string): string {
  return stockCode.trim().toLowerCase()
}

function resolveRangeDays(period: KLinePeriod, rangeDays?: number): number {
  if (rangeDays && rangeDays > 0) {
    return rangeDays
  }
  if (period === 'MONTHLY') {
    return 365 * 5
  }
  if (period === 'WEEKLY') {
    return 365 * 3
  }
  return 365
}

export const useMarketStore = defineStore('market', () => {
  const quoteMap = ref<Record<string, Quote>>({})
  const watchCodes = ref<string[]>([...DEFAULT_CODES])
  const searchResults = ref<Quote[]>([])
  const selectedCode = ref(DEFAULT_CODES[0])
  const selectedPeriod = ref<KLinePeriod>('DAILY')
  const klinePoints = ref<KLinePoint[]>([])
  const loadingQuotes = ref(false)
  const loadingKLine = ref(false)
  const loadingSearch = ref(false)

  const authStore = useAuthStore()

  const ws = useWebSocket({
    endpoint: '/ws/market',
    getToken: () => authStore.accessToken || null,
    onQuote: (quote) => {
      upsertQuote(quote)
    },
  })

  const watchQuotes = computed(() => {
    return watchCodes.value
      .map((code) => quoteMap.value[normalizeCode(code)])
      .filter((quote): quote is Quote => Boolean(quote))
  })

  const selectedQuote = computed(() => quoteMap.value[normalizeCode(selectedCode.value)] ?? null)
  let visibleHeartbeatTimer: number | null = null

  function upsertQuote(quote: Quote): void {
    quoteMap.value = {
      ...quoteMap.value,
      [normalizeCode(quote.stockCode)]: quote,
    }
  }

  function setSelectedCode(stockCode: string): void {
    const previous = normalizeCode(selectedCode.value)
    const next = normalizeCode(stockCode)
    selectedCode.value = next

    if (previous && previous !== next && !watchCodes.value.includes(previous)) {
      ws.unsubscribeQuote(previous)
    }
    if (next) {
      ws.subscribeQuote(next)
    }
    void reportVisibleCodes()
  }

  function setWatchCodes(codes: string[]): void {
    const previousCodes = new Set(watchCodes.value)
    const normalized = Array.from(new Set(codes.map(normalizeCode).filter((code) => code.length > 0)))
    watchCodes.value = normalized.length > 0 ? normalized : [...DEFAULT_CODES]
    const nextCodes = new Set(watchCodes.value)

    for (const code of previousCodes) {
      if (!nextCodes.has(code) && code !== normalizeCode(selectedCode.value)) {
        ws.unsubscribeQuote(code)
      }
    }

    for (const code of watchCodes.value) {
      ws.subscribeQuote(code)
    }
    void reportVisibleCodes()
  }

  function collectVisibleCodes(): string[] {
    const merged = [...watchCodes.value]
    const selected = normalizeCode(selectedCode.value)
    if (selected) {
      merged.push(selected)
    }
    return Array.from(new Set(merged.filter((code) => code.length > 0)))
  }

  async function reportVisibleCodes(): Promise<void> {
    if (!authStore.isAuthenticated) {
      return
    }
    const visibleCodes = collectVisibleCodes()
    if (visibleCodes.length === 0) {
      return
    }
    try {
      await marketApi.reportVisibleCodes(visibleCodes)
    } catch (_error) {
      // 可见股票上报失败不阻断行情展示主流程
    }
  }

  function startVisibleHeartbeat(): void {
    if (visibleHeartbeatTimer !== null) {
      return
    }
    void reportVisibleCodes()
    visibleHeartbeatTimer = window.setInterval(() => {
      void reportVisibleCodes()
    }, 1500)
  }

  function stopVisibleHeartbeat(): void {
    if (visibleHeartbeatTimer === null) {
      return
    }
    window.clearInterval(visibleHeartbeatTimer)
    visibleHeartbeatTimer = null
  }

  async function loadWatchQuotes(): Promise<void> {
    loadingQuotes.value = true
    try {
      const quotes = await marketApi.batchGetQuotes(watchCodes.value)
      for (const quote of quotes) {
        upsertQuote(quote)
      }
    } finally {
      loadingQuotes.value = false
    }
  }

  async function loadQuote(stockCode: string): Promise<void> {
    const quote = await marketApi.getQuote(normalizeCode(stockCode))
    upsertQuote(quote)
  }

  async function loadKLine(
    stockCode: string,
    period = selectedPeriod.value,
    rangeDays?: number,
  ): Promise<void> {
    loadingKLine.value = true
    selectedPeriod.value = period

    try {
      const effectiveRangeDays = resolveRangeDays(period, rangeDays)
      const to = dayjs().format('YYYY-MM-DD')
      const from = dayjs().subtract(effectiveRangeDays, 'day').format('YYYY-MM-DD')
      klinePoints.value = await marketApi.getKLine(normalizeCode(stockCode), period, from, to)
    } finally {
      loadingKLine.value = false
    }
  }

  async function search(keyword: string): Promise<void> {
    const trimmed = keyword.trim()
    if (!trimmed) {
      searchResults.value = []
      return
    }

    loadingSearch.value = true
    try {
      searchResults.value = await marketApi.searchStocks(trimmed)
    } finally {
      loadingSearch.value = false
    }
  }

  async function initializeMarket(): Promise<void> {
    if (!authStore.isAuthenticated) {
      return
    }

    ws.connect()
    startVisibleHeartbeat()
    for (const code of watchCodes.value) {
      ws.subscribeQuote(code)
    }

    await loadWatchQuotes()

    if (selectedCode.value) {
      await loadQuote(selectedCode.value)
    }
  }

  function connectRealtime(): void {
    if (!authStore.isAuthenticated) {
      return
    }

    ws.connect()
    startVisibleHeartbeat()
    for (const code of watchCodes.value) {
      ws.subscribeQuote(code)
    }
    if (selectedCode.value) {
      ws.subscribeQuote(selectedCode.value)
    }
  }

  function disconnectRealtime(): void {
    stopVisibleHeartbeat()
    ws.disconnect()
  }

  return {
    quoteMap,
    watchCodes,
    searchResults,
    selectedCode,
    selectedPeriod,
    klinePoints,
    loadingQuotes,
    loadingKLine,
    loadingSearch,
    watchQuotes,
    selectedQuote,
    wsStatus: ws.status,
    wsLagMs: ws.lastLagMs,
    wsDegraded: ws.isDegraded,
    setWatchCodes,
    setSelectedCode,
    loadWatchQuotes,
    loadQuote,
    loadKLine,
    search,
    initializeMarket,
    connectRealtime,
    disconnectRealtime,
  }
})
