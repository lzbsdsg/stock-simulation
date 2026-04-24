import { computed, ref } from 'vue'
import dayjs from 'dayjs'
import { defineStore } from 'pinia'
import * as marketApi from '@/api/market'
import { useWebSocket } from '@/composables/useWebSocket'
import { useAuthStore } from '@/stores/auth'
import type { KLinePeriod, KLinePoint, MarketListedItem, Quote } from '@/types/market'

const DEFAULT_CODES = ['sh600519', 'sz000001', 'sh601318', 'sh600036']
const LISTED_UNIVERSE_CACHE_TTL_MS = 10 * 60 * 1000
const VISIBLE_CODES_HEARTBEAT_MS = 15000
const MARKET_NATIVE_ENDPOINT = '/ws/market-native'

interface LoadKLineOptions {
  preferCache?: boolean
  backgroundRefresh?: boolean
}

interface LoadQuoteOptions {
  preferCache?: boolean
  backgroundRefresh?: boolean
}

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

function normalizeCodes(codes: string[]): string[] {
  return Array.from(new Set(codes.map(normalizeCode).filter((code) => code.length > 0)))
}

function mergeCodeSources(...sources: string[][]): string[] {
  return normalizeCodes(sources.flat())
}

export const useMarketStore = defineStore('market', () => {
  const quoteMap = ref<Record<string, Quote>>({})
  const watchlistCodes = ref<string[]>([])
  const marketPageCodes = ref<string[]>([])
  const hotspotCodes = ref<string[]>([...DEFAULT_CODES])
  const searchResults = ref<Quote[]>([])
  const selectedCode = ref(DEFAULT_CODES[0])
  const selectedPeriod = ref<KLinePeriod>('DAILY')
  const klinePoints = ref<KLinePoint[]>([])
  const klineCache = ref<Record<string, KLinePoint[]>>({})
  const listedUniverseCache = ref<MarketListedItem[]>([])
  const listedUniverseCachedAt = ref(0)
  const loadingQuotes = ref(false)
  const loadingKLine = ref(false)
  const loadingSearch = ref(false)

  const authStore = useAuthStore()

  const ws = useWebSocket({
    endpoint: MARKET_NATIVE_ENDPOINT,
    getToken: () => authStore.accessToken || null,
    onQuotes: (quotes) => {
      mergeQuotes(quotes)
    },
  })

  const realtimeCodes = computed(() =>
    mergeCodeSources(watchlistCodes.value, marketPageCodes.value, hotspotCodes.value),
  )

  const displayCodes = computed(() => {
    if (marketPageCodes.value.length > 0) {
      return marketPageCodes.value
    }
    if (watchlistCodes.value.length > 0) {
      return watchlistCodes.value
    }
    return hotspotCodes.value
  })

  const watchCodes = computed(() => displayCodes.value)

  const watchQuotes = computed(() => {
    return displayCodes.value
      .map((code) => quoteMap.value[normalizeCode(code)])
      .filter((quote): quote is Quote => Boolean(quote))
  })

  const selectedQuote = computed(() => quoteMap.value[normalizeCode(selectedCode.value)] ?? null)
  let visibleHeartbeatTimer: number | null = null
  let subscribedRealtimeCodes: string[] = []
  const quoteInFlight = new Map<string, Promise<Quote>>()
  const klineInFlight = new Map<string, Promise<KLinePoint[]>>()
  let latestKLineRequestToken = 0

  function upsertQuote(quote: Quote): void {
    const code = normalizeCode(quote.stockCode)
    const existing = quoteMap.value[code]
    const normalizedName = quote.stockName?.trim()
    quoteMap.value[code] = {
      ...quote,
      stockCode: code,
      stockName: normalizedName && normalizedName.length > 0 ? normalizedName : (existing?.stockName ?? code),
    }
  }

  function mergeQuotes(quotes: Quote[]): void {
    for (const quote of quotes) {
      upsertQuote(quote)
    }
  }

  function setSelectedCode(stockCode: string): void {
    const previous = normalizeCode(selectedCode.value)
    const next = normalizeCode(stockCode)
    selectedCode.value = next

    if (previous && previous !== next && !subscribedRealtimeCodes.includes(previous)) {
      ws.unsubscribeQuote(previous)
    }
    if (next) {
      ws.subscribeQuote(next)
    }
    void reportVisibleCodes()
  }

  function syncRealtimeSubscriptions(): void {
    const previousCodes = new Set(subscribedRealtimeCodes)
    const nextCodes = new Set(realtimeCodes.value)
    subscribedRealtimeCodes = Array.from(nextCodes)

    for (const code of previousCodes) {
      if (!nextCodes.has(code) && code !== normalizeCode(selectedCode.value)) {
        ws.unsubscribeQuote(code)
      }
    }

    for (const code of subscribedRealtimeCodes) {
      ws.subscribeQuote(code)
    }
    void reportVisibleCodes()
  }

  function setWatchlistCodes(codes: string[]): void {
    watchlistCodes.value = normalizeCodes(codes)
    syncRealtimeSubscriptions()
  }

  function setMarketPageCodes(codes: string[]): void {
    marketPageCodes.value = normalizeCodes(codes)
    syncRealtimeSubscriptions()
  }

  function setHotspotCodes(codes: string[]): void {
    hotspotCodes.value = normalizeCodes(codes)
    syncRealtimeSubscriptions()
  }

  function setListedUniverseCache(items: MarketListedItem[]): void {
    listedUniverseCache.value = [...items]
    listedUniverseCachedAt.value = Date.now()
  }

  function getListedUniverseCache(maxAgeMs = LISTED_UNIVERSE_CACHE_TTL_MS): MarketListedItem[] | null {
    if (listedUniverseCache.value.length === 0 || listedUniverseCachedAt.value <= 0) {
      return null
    }
    if (Date.now() - listedUniverseCachedAt.value > maxAgeMs) {
      return null
    }
    return [...listedUniverseCache.value]
  }

  // 保留旧接口以避免现有调用点一次性迁移带来回归。
  function setWatchCodes(codes: string[]): void {
    setWatchlistCodes(codes)
  }

  function collectVisibleCodes(): string[] {
    const merged = [...realtimeCodes.value]
    const selected = normalizeCode(selectedCode.value)
    if (selected) {
      merged.push(selected)
    }
    return Array.from(new Set(merged.filter((code) => code.length > 0)))
  }

  function shouldReportVisibleCodes(): boolean {
    if (typeof document === 'undefined') {
      return true
    }
    return document.visibilityState === 'visible' && statusAllowsHeartbeat()
  }

  function statusAllowsHeartbeat(): boolean {
    return ws.status.value === 'CONNECTED' || ws.status.value === 'CONNECTING'
  }

  async function reportVisibleCodes(): Promise<void> {
    if (!authStore.isAuthenticated || !shouldReportVisibleCodes()) {
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
      if (!shouldReportVisibleCodes()) {
        return
      }
      void reportVisibleCodes()
    }, VISIBLE_CODES_HEARTBEAT_MS)
  }

  function stopVisibleHeartbeat(): void {
    if (visibleHeartbeatTimer === null) {
      return
    }
    window.clearInterval(visibleHeartbeatTimer)
    visibleHeartbeatTimer = null
  }

  async function loadWatchQuotes(silent = false): Promise<void> {
    if (displayCodes.value.length === 0) {
      return
    }
    if (!silent) {
      loadingQuotes.value = true
    }
    try {
      const quotes = await marketApi.batchGetQuotes(displayCodes.value)
      mergeQuotes(quotes)
    } finally {
      if (!silent) {
        loadingQuotes.value = false
      }
    }
  }

  async function loadWatchlistQuotes(): Promise<void> {
    if (watchlistCodes.value.length === 0) {
      return
    }
    loadingQuotes.value = true
    try {
      const quotes = await marketApi.batchGetQuotes(watchlistCodes.value)
      mergeQuotes(quotes)
    } finally {
      loadingQuotes.value = false
    }
  }

  function getQuoteTask(normalizedCode: string): Promise<Quote> {
    const existing = quoteInFlight.get(normalizedCode)
    if (existing) {
      return existing
    }

    const task = marketApi.getQuote(normalizedCode)
    quoteInFlight.set(normalizedCode, task)
    return task.finally(() => {
      quoteInFlight.delete(normalizedCode)
    })
  }

  function getKLineTask(
    requestKey: string,
    normalizedCode: string,
    period: KLinePeriod,
    from: string,
    to: string,
  ): Promise<KLinePoint[]> {
    const existing = klineInFlight.get(requestKey)
    if (existing) {
      return existing
    }

    const task = marketApi.getKLine(normalizedCode, period, from, to)
    klineInFlight.set(requestKey, task)
    return task.finally(() => {
      klineInFlight.delete(requestKey)
    })
  }

  async function loadQuote(stockCode: string, options: LoadQuoteOptions = {}): Promise<void> {
    const normalizedCode = normalizeCode(stockCode)
    const cached = quoteMap.value[normalizedCode]
    const preferCache = options.preferCache === true
    const backgroundRefresh = options.backgroundRefresh !== false

    if (preferCache && cached) {
      if (!backgroundRefresh) {
        return
      }
      void getQuoteTask(normalizedCode)
        .then((quote) => {
          upsertQuote(quote)
        })
        .catch(() => undefined)
      return
    }

    const quote = await getQuoteTask(normalizedCode)
    upsertQuote(quote)
  }

  async function loadKLine(
    stockCode: string,
    period = selectedPeriod.value,
    rangeDays?: number,
    options: LoadKLineOptions = {},
  ): Promise<void> {
    const effectiveRangeDays = resolveRangeDays(period, rangeDays)
    const normalizedCode = normalizeCode(stockCode)
    const cacheKey = `${normalizedCode}|${period}|${effectiveRangeDays}`
    const to = dayjs().format('YYYY-MM-DD')
    const from = dayjs().subtract(effectiveRangeDays, 'day').format('YYYY-MM-DD')
    const requestKey = `${cacheKey}|${from}|${to}`
    const cachedPoints = klineCache.value[cacheKey]
    const preferCache = options.preferCache === true
    const backgroundRefresh = options.backgroundRefresh !== false
    selectedPeriod.value = period

    if (preferCache && cachedPoints && cachedPoints.length > 0) {
      klinePoints.value = cachedPoints
      if (!backgroundRefresh) {
        return
      }

      const requestToken = ++latestKLineRequestToken
      void getKLineTask(requestKey, normalizedCode, period, from, to)
        .then((points) => {
          klineCache.value[cacheKey] = points
          if (requestToken === latestKLineRequestToken) {
            klinePoints.value = points
          }
        })
        .catch(() => undefined)
      return
    }

    const requestToken = ++latestKLineRequestToken
    loadingKLine.value = true

    try {
      const points = await getKLineTask(requestKey, normalizedCode, period, from, to)
      klineCache.value[cacheKey] = points
      if (requestToken === latestKLineRequestToken) {
        klinePoints.value = points
      }
    } finally {
      if (requestToken === latestKLineRequestToken) {
        loadingKLine.value = false
      }
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
    syncRealtimeSubscriptions()

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
    syncRealtimeSubscriptions()
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
    watchlistCodes,
    marketPageCodes,
    hotspotCodes,
    realtimeCodes,
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
    setWatchlistCodes,
    setMarketPageCodes,
    setHotspotCodes,
    setListedUniverseCache,
    getListedUniverseCache,
    setWatchCodes,
    setSelectedCode,
    loadWatchQuotes,
    loadWatchlistQuotes,
    mergeQuotes,
    loadQuote,
    loadKLine,
    search,
    initializeMarket,
    connectRealtime,
    disconnectRealtime,
  }
})
