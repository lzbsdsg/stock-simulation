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

  function upsertQuote(quote: Quote): void {
    quoteMap.value = {
      ...quoteMap.value,
      [normalizeCode(quote.stockCode)]: quote,
    }
  }

  function setSelectedCode(stockCode: string): void {
    selectedCode.value = normalizeCode(stockCode)
    ws.subscribeQuote(selectedCode.value)
  }

  function setWatchCodes(codes: string[]): void {
    const normalized = Array.from(new Set(codes.map(normalizeCode).filter((code) => code.length > 0)))
    watchCodes.value = normalized.length > 0 ? normalized : [...DEFAULT_CODES]

    for (const code of watchCodes.value) {
      ws.subscribeQuote(code)
    }
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
    for (const code of watchCodes.value) {
      ws.subscribeQuote(code)
    }
    if (selectedCode.value) {
      ws.subscribeQuote(selectedCode.value)
    }
  }

  function disconnectRealtime(): void {
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
