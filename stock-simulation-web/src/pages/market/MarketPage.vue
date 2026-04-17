<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import StockSearch from '@/components/market/StockSearch.vue'
import QuoteCard from '@/components/market/QuoteCard.vue'
import MarketOverview from '@/components/market/MarketOverview.vue'
import * as marketApi from '@/api/market'
import { useAppStore } from '@/stores/app'
import { useMarketStore } from '@/stores/market'
import { formatPercent, formatPrice, formatVolume, percentClass } from '@/utils/format'
import type { MarketIndexQuote, MarketListedItem } from '@/types/market'

const PAGE_SIZE_OPTIONS = [30, 40]
const BOARD_REFRESH_MS = 5000
const QUOTE_REFRESH_MS = 3000
const LIST_FETCH_BATCH_SIZE = 200
const MARKET_PAGE_STATE_KEY = 'market:page-state:v1'

const router = useRouter()
const marketStore = useMarketStore()
const appStore = useAppStore()
const currentPage = ref(1)
const pageSize = ref(40)
const totalStocks = ref(0)
const loadingPageQuotes = ref(false)
const loadingOfficialBoard = ref(false)
const marketIndexes = ref<MarketIndexQuote[]>([])
const listedUniverse = ref<MarketListedItem[]>([])
let boardRefreshTimer: number | null = null
let quoteRefreshTimer: number | null = null

const rateLimitText = computed(() => {
  const info = appStore.lastRateLimit
  return `limit=${info.limit ?? '-'} | remaining=${info.remaining ?? '-'} | reset=${info.reset ?? '-'}`
})

const wsStatusClass = computed(() => (marketStore.wsStatus === 'CONNECTED' ? 'up' : 'down'))

const latencyClass = computed(() => {
  if (marketStore.wsLagMs > 5000) {
    return 'down'
  }
  if (marketStore.wsLagMs > 1500) {
    return 'flat'
  }
  return 'up'
})

const marketQuotes = computed(() => marketStore.watchQuotes)

const riseCount = computed(() => marketQuotes.value.filter((item) => (item.changePercent ?? 0) > 0).length)
const fallCount = computed(() => marketQuotes.value.filter((item) => (item.changePercent ?? 0) < 0).length)

const avgChangePercent = computed(() => {
  if (marketQuotes.value.length === 0) {
    return null
  }
  const total = marketQuotes.value.reduce((sum, item) => sum + (item.changePercent ?? 0), 0)
  return total / marketQuotes.value.length
})

const pulseClass = computed(() => {
  if ((avgChangePercent.value ?? 0) > 0) {
    return 'pill-safe'
  }
  if ((avgChangePercent.value ?? 0) < 0) {
    return 'pill-risk'
  }
  return 'pill-brand'
})

const quoteGridLoading = computed(() => loadingPageQuotes.value || marketStore.loadingQuotes)

const totalPages = computed(() => {
  if (totalStocks.value <= 0) {
    return 1
  }
  return Math.ceil(totalStocks.value / pageSize.value)
})

const pageStart = computed(() => {
  if (totalStocks.value === 0) {
    return 0
  }
  return (currentPage.value - 1) * pageSize.value + 1
})

const pageEnd = computed(() => {
  if (totalStocks.value === 0) {
    return 0
  }
  return Math.min(totalStocks.value, currentPage.value * pageSize.value)
})

onMounted(async () => {
  try {
    restorePageState()
    await marketStore.initializeMarket()
    await loadListedUniverse()
    await applyLocalPage(currentPage.value)
    void loadOfficialBoard({ showError: false, showLoading: true })
    startBoardRefresh()
    startQuoteRefresh()
  } catch (error) {
    const message = error instanceof Error ? error.message : '行情初始化失败'
    ElMessage.error(message)
  }
})

onUnmounted(() => {
  persistPageState()
  stopBoardRefresh()
  stopQuoteRefresh()
  marketStore.setMarketPageCodes([])
})

function startBoardRefresh(): void {
  if (boardRefreshTimer !== null) {
    return
  }
  boardRefreshTimer = window.setInterval(() => {
    if (document.hidden) {
      return
    }
    void loadOfficialBoard({ showError: false, showLoading: false })
  }, BOARD_REFRESH_MS)
}

function stopBoardRefresh(): void {
  if (boardRefreshTimer === null) {
    return
  }
  window.clearInterval(boardRefreshTimer)
  boardRefreshTimer = null
}

function startQuoteRefresh(): void {
  if (quoteRefreshTimer !== null) {
    return
  }
  quoteRefreshTimer = window.setInterval(() => {
    if (document.hidden) {
      return
    }
    void marketStore.loadWatchQuotes(true)
  }, QUOTE_REFRESH_MS)
}

function stopQuoteRefresh(): void {
  if (quoteRefreshTimer === null) {
    return
  }
  window.clearInterval(quoteRefreshTimer)
  quoteRefreshTimer = null
}

function handleSelectStock(stockCode: string): void {
  marketStore.setSelectedCode(stockCode)
  void marketStore.loadQuote(stockCode).catch(() => undefined)
  router.push(`/market/${stockCode}`)
}

function isSameIndexes(left: MarketIndexQuote[], right: MarketIndexQuote[]): boolean {
  if (left.length !== right.length) {
    return false
  }
  return left.every((item, index) => {
    const target = right[index]
    return target
      && item.stockCode === target.stockCode
      && item.currentPrice === target.currentPrice
      && item.changeAmount === target.changeAmount
      && item.changePercent === target.changePercent
      && item.volume === target.volume
      && item.amount === target.amount
  })
}

function patchBoardData(indexes: MarketIndexQuote[]): void {
  if (!isSameIndexes(marketIndexes.value, indexes)) {
    marketIndexes.value = indexes
  }
}

async function loadListedUniverse(): Promise<void> {
  const cached = marketStore.getListedUniverseCache()
  if (cached && cached.length > 0) {
    listedUniverse.value = cached
    totalStocks.value = cached.length
    return
  }

  const first = await marketApi.getListedStocksPage(1, LIST_FETCH_BATCH_SIZE)
  const records = [...first.records]
  const total = first.total
  const totalFetchPages = Math.max(1, Math.ceil(total / LIST_FETCH_BATCH_SIZE))
  for (let page = 2; page <= totalFetchPages; page += 1) {
    const next = await marketApi.getListedStocksPage(page, LIST_FETCH_BATCH_SIZE)
    records.push(...next.records)
  }
  listedUniverse.value = records
  totalStocks.value = records.length
  marketStore.setListedUniverseCache(records)
}

async function applyLocalPage(page: number): Promise<void> {
  loadingPageQuotes.value = true
  try {
    if (listedUniverse.value.length === 0) {
      return
    }
    const safePage = Math.min(Math.max(page, 1), totalPages.value)
    currentPage.value = safePage
    const startIndex = (safePage - 1) * pageSize.value
    const endIndex = startIndex + pageSize.value
    const currentRecords = listedUniverse.value.slice(startIndex, endIndex)
    const currentCodes = currentRecords.map((item) => item.stockCode)
    marketStore.setMarketPageCodes(currentCodes)

    const hasAllCachedQuotes =
      currentCodes.length > 0
      && currentCodes.every((code) => Boolean(marketStore.quoteMap[code]))

    if (hasAllCachedQuotes) {
      void marketStore.loadWatchQuotes(true)
    } else {
      await marketStore.loadWatchQuotes()
    }
  } finally {
    loadingPageQuotes.value = false
  }
}

function restorePageState(): void {
  try {
    const raw = window.sessionStorage.getItem(MARKET_PAGE_STATE_KEY)
    if (!raw) {
      return
    }
    const parsed = JSON.parse(raw) as { page?: unknown; pageSize?: unknown }
    const restoredPage = Number(parsed.page)
    const restoredPageSize = Number(parsed.pageSize)
    if (Number.isFinite(restoredPage) && restoredPage > 0) {
      currentPage.value = Math.floor(restoredPage)
    }
    if (PAGE_SIZE_OPTIONS.includes(restoredPageSize)) {
      pageSize.value = restoredPageSize
    }
  } catch (_error) {
    // 会话状态恢复失败时保持默认分页，不阻断主流程
  }
}

function persistPageState(): void {
  try {
    window.sessionStorage.setItem(
      MARKET_PAGE_STATE_KEY,
      JSON.stringify({
        page: currentPage.value,
        pageSize: pageSize.value,
      }),
    )
  } catch (_error) {
    // 会话状态保存失败不影响行情主流程
  }
}

async function loadOfficialBoard(options?: {
  showError?: boolean
  showLoading?: boolean
}): Promise<void> {
  const showError = options?.showError ?? true
  const showLoading = options?.showLoading ?? true

  if (showLoading) {
    loadingOfficialBoard.value = true
  }
  try {
    const indexes = await marketApi.getOfficialIndexQuotes()
    patchBoardData(indexes)
  } catch (_error) {
    if (showError) {
      ElMessage.warning('大盘指数暂时不可用，页面已保留上次成功数据。')
    }
  } finally {
    if (showLoading) {
      loadingOfficialBoard.value = false
    }
  }
}

function handlePageChange(page: number): void {
  currentPage.value = page
  persistPageState()
  void applyLocalPage(page)
}

function handlePageSizeChange(size: number): void {
  pageSize.value = size
  persistPageState()
  void applyLocalPage(1)
}

async function handleRefresh(): Promise<void> {
  try {
    await Promise.all([
      marketStore.loadWatchQuotes(),
      loadOfficialBoard({ showError: false, showLoading: false }),
    ])
    ElMessage.success('行情已刷新')
  } catch (error) {
    const message = error instanceof Error ? error.message : '刷新失败'
    ElMessage.error(message)
  }
}
</script>

<template>
  <section class="market-page">
    <header class="page-head market-head">
      <div>
        <h1 class="page-title">行情中心</h1>
        <p class="page-subtitle">按市场状态分区展示，提升盘中扫描效率与重点识别速度。</p>
      </div>
      <div class="market-head-actions">
        <el-button plain @click="handleRefresh">刷新行情</el-button>
      </div>
    </header>

    <section class="kpi-strip">
      <span class="kpi-pill" :class="pulseClass">
        市场脉冲
        <strong :class="(avgChangePercent ?? 0) >= 0 ? 'up' : 'down'">{{ formatPercent(avgChangePercent) }}</strong>
      </span>
      <span class="kpi-pill pill-safe">
        上涨数量
        <strong class="mono-number up">{{ riseCount }}</strong>
      </span>
      <span class="kpi-pill pill-risk">
        下跌数量
        <strong class="mono-number down">{{ fallCount }}</strong>
      </span>
      <span class="kpi-pill pill-brand">
        当前扫描
        <strong class="mono-number">{{ pageStart }} - {{ pageEnd }}</strong>
      </span>
    </section>

    <section class="market-top-grid">
      <section class="section-card market-search-panel">
        <div class="section-card-head">
          <div>
            <h2 class="section-card-title">股票检索</h2>
            <p class="section-card-subtitle">输入代码/名称后直接跳转到详情页</p>
          </div>
          <span class="panel-tag">实时查询</span>
        </div>
        <StockSearch @select="handleSelectStock" />
      </section>

      <section class="section-card market-status-panel">
        <div class="section-card-head">
          <div>
            <h2 class="section-card-title">链路状态</h2>
            <p class="section-card-subtitle">连接、延迟、缓存与限流头</p>
          </div>
        </div>

        <div class="market-status-list">
          <article class="metric-tile">
            <span class="metric-label">WS 连接状态</span>
            <strong class="metric-value" :class="wsStatusClass">{{ marketStore.wsStatus }}</strong>
          </article>
          <article class="metric-tile">
            <span class="metric-label">推送延迟</span>
            <strong class="metric-value" :class="latencyClass">{{ marketStore.wsLagMs }} ms</strong>
          </article>
          <article class="metric-tile">
            <span class="metric-label">降级标记</span>
            <strong class="metric-value" :class="marketStore.wsDegraded ? 'down' : 'up'">
              {{ marketStore.wsDegraded ? '已降级' : '正常' }}
            </strong>
          </article>
          <article class="metric-tile">
            <span class="metric-label">缓存新鲜度</span>
            <strong class="metric-value">{{ appStore.lastCacheStatus || 'N/A' }}</strong>
          </article>
        </div>

        <el-alert class="market-alert" type="info" :closable="false" show-icon>
          <template #title>限流头信息</template>
          {{ rateLimitText }}
        </el-alert>
      </section>
    </section>

    <section v-loading="loadingOfficialBoard" class="section-card market-index-panel">
      <div class="section-card-head">
        <div>
          <h2 class="section-card-title">市场指数看板</h2>
          <p class="section-card-subtitle">追踪指数级别的价格变化、量能和成交额</p>
        </div>
      </div>

      <section class="market-board-grid">
        <article v-for="index in marketIndexes" :key="index.stockCode" class="market-board-card">
          <header>
            <h3>{{ index.stockName }}</h3>
            <small>{{ index.stockCode.toUpperCase() }}</small>
          </header>
          <strong class="mono-number">{{ formatPrice(index.currentPrice) }}</strong>
          <div class="market-board-row">
            <span :class="percentClass(index.changePercent)">{{ formatPercent(index.changePercent) }}</span>
            <span :class="percentClass(index.changeAmount)">{{ formatPrice(index.changeAmount) }}</span>
          </div>
          <footer>
            <span>量 {{ formatVolume(index.volume) }}</span>
            <span>额 {{ formatVolume(index.amount) }}</span>
          </footer>
        </article>
      </section>
    </section>

    <section class="section-card market-breadth-panel">
      <div class="section-card-head">
        <div>
          <h2 class="section-card-title">市场广度</h2>
          <p class="section-card-subtitle">上涨/下跌分布与整体成交活跃度</p>
        </div>
      </div>
      <MarketOverview :quotes="marketQuotes" />
    </section>

    <section v-loading="quoteGridLoading" class="section-card market-list-panel">
      <div class="section-card-head">
        <div>
          <h2 class="section-card-title">股票扫描区</h2>
          <p class="section-card-subtitle">当前页 {{ pageStart }} - {{ pageEnd }} / 共 {{ totalStocks }} 只</p>
        </div>
      </div>

      <section class="quote-grid">
        <QuoteCard
          v-for="quote in marketQuotes"
          :key="quote.stockCode"
          :quote="quote"
          @select="handleSelectStock"
        />
      </section>
    </section>

    <section class="market-pagination section-card">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next"
        :total="totalStocks"
        :page-size="pageSize"
        :page-sizes="PAGE_SIZE_OPTIONS"
        :current-page="currentPage"
        @current-change="handlePageChange"
        @size-change="handlePageSizeChange"
      />
      <small>分页策略：本地分页（股票池加载后按当前页切换实时订阅）。</small>
      <small>共 {{ totalPages }} 页，当前页股票会实时订阅，切页后自动切换订阅集合。</small>
    </section>
  </section>
</template>

<style scoped>
.market-page {
  display: grid;
  gap: 14px;
}

.market-head-actions {
  display: flex;
  gap: 8px;
}

.market-top-grid {
  display: grid;
  gap: 14px;
  grid-template-columns: minmax(0, 1.12fr) minmax(320px, 1fr);
}

.market-status-panel {
  align-content: start;
}

.market-status-list {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.market-alert {
  margin-top: 6px;
}

.market-index-panel,
.market-breadth-panel,
.market-list-panel {
  align-content: start;
}

@media (max-width: 1120px) {
  .market-top-grid {
    grid-template-columns: 1fr;
  }

  .market-status-list {
    grid-template-columns: 1fr;
  }
}
</style>
