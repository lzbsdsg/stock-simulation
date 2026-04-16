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
import type { MarketIndexQuote, MarketListedItem, MarketRankPayload } from '@/types/market'

const PAGE_SIZE_OPTIONS = [30, 40]
const RANK_LIMIT = 10
const BOARD_REFRESH_MS = 30000
const LIST_FETCH_BATCH_SIZE = 200

const router = useRouter()
const marketStore = useMarketStore()
const appStore = useAppStore()
const currentPage = ref(1)
const pageSize = ref(40)
const totalStocks = ref(0)
const loadingPageQuotes = ref(false)
const loadingOfficialBoard = ref(false)
const marketIndexes = ref<MarketIndexQuote[]>([])
const rankBoard = ref<MarketRankPayload>({ gainers: [], losers: [] })
const listedUniverse = ref<MarketListedItem[]>([])
const boardDataSource = ref<'official' | 'unavailable'>('official')
const officialBoardUnavailable = ref(false)
let boardRefreshTimer: number | null = null

const rateLimitText = computed(() => {
  const info = appStore.lastRateLimit
  return `limit=${info.limit ?? '-'} | remaining=${info.remaining ?? '-'} | reset=${info.reset ?? '-'}`
})

const marketQuotes = computed(() => marketStore.watchQuotes)

const quoteGridLoading = computed(() => loadingPageQuotes.value || marketStore.loadingQuotes)

const riseRankList = computed(() => rankBoard.value.gainers)

const fallRankList = computed(() => rankBoard.value.losers)

const totalPages = computed(() => {
  if (totalStocks.value <= 0) {
    return 1
  }
  return Math.ceil(totalStocks.value / pageSize.value)
})

onMounted(async () => {
  try {
    await marketStore.initializeMarket()
    await Promise.all([loadListedUniverse(), loadOfficialBoard()])
    await applyLocalPage(1)
    startBoardRefresh()
  } catch (error) {
    const message = error instanceof Error ? error.message : '行情初始化失败'
    ElMessage.error(message)
  }
})

onUnmounted(() => {
  stopBoardRefresh()
  marketStore.setMarketPageCodes([])
  void marketStore.loadWatchlistQuotes()
})

function startBoardRefresh(): void {
  if (boardRefreshTimer !== null) {
    return
  }
  boardRefreshTimer = window.setInterval(() => {
    if (document.hidden) {
      return
    }
    void loadOfficialBoard({ showError: false, showLoading: false, allowOfficialRetry: true })
  }, BOARD_REFRESH_MS)
}

function stopBoardRefresh(): void {
  if (boardRefreshTimer === null) {
    return
  }
  window.clearInterval(boardRefreshTimer)
  boardRefreshTimer = null
}

function handleSelectStock(stockCode: string): void {
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

function isSameRankBoard(left: MarketRankPayload, right: MarketRankPayload): boolean {
  const leftItems = [...left.gainers, ...left.losers]
  const rightItems = [...right.gainers, ...right.losers]
  if (leftItems.length !== rightItems.length) {
    return false
  }
  return leftItems.every((item, index) => {
    const target = rightItems[index]
    return target
      && item.stockCode === target.stockCode
      && item.currentPrice === target.currentPrice
      && item.changePercent === target.changePercent
  })
}

function patchBoardData(indexes: MarketIndexQuote[], rank: MarketRankPayload): void {
  if (!isSameIndexes(marketIndexes.value, indexes)) {
    marketIndexes.value = indexes
  }
  if (!isSameRankBoard(rankBoard.value, rank)) {
    rankBoard.value = rank
  }
}

async function loadListedUniverse(): Promise<void> {
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
    await marketStore.loadWatchQuotes()
  } finally {
    loadingPageQuotes.value = false
  }
}

async function loadOfficialBoard(options?: {
  showError?: boolean
  showLoading?: boolean
  allowOfficialRetry?: boolean
}): Promise<void> {
  const showError = options?.showError ?? true
  const showLoading = options?.showLoading ?? true
  const allowOfficialRetry = options?.allowOfficialRetry ?? true

  if (officialBoardUnavailable.value && !allowOfficialRetry) {
    return
  }

  if (showLoading) {
    loadingOfficialBoard.value = true
  }
  try {
    const [indexes, rank] = await Promise.all([
      marketApi.getOfficialIndexQuotes(),
      marketApi.getOfficialRankBoard(RANK_LIMIT),
    ])
    boardDataSource.value = 'official'
    officialBoardUnavailable.value = false
    patchBoardData(indexes, rank)
  } catch (_error) {
    officialBoardUnavailable.value = true
    boardDataSource.value = 'unavailable'
    if (showError) {
      ElMessage.warning('官方涨跌榜暂时不可用，页面已保留上次成功数据。')
    }
  } finally {
    if (showLoading) {
      loadingOfficialBoard.value = false
    }
  }
}

function handlePageChange(page: number): void {
  void applyLocalPage(page)
}

function handlePageSizeChange(size: number): void {
  pageSize.value = size
  void applyLocalPage(1)
}

async function handleRefresh(): Promise<void> {
  try {
    await Promise.all([
      marketStore.loadWatchQuotes(),
      loadOfficialBoard({ showError: false, showLoading: false, allowOfficialRetry: true }),
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
    <header class="market-header">
      <div>
        <h1>行情中心</h1>
        <p>支持股票搜索、实时推送、缓存状态监控与详情跳转。</p>
      </div>
      <el-button type="primary" plain @click="handleRefresh">刷新行情</el-button>
    </header>

    <section class="market-meta-grid">
      <article class="meta-card">
        <span>WS 连接状态</span>
        <strong>{{ marketStore.wsStatus }}</strong>
      </article>
      <article class="meta-card">
        <span>推送延迟</span>
        <strong>{{ marketStore.wsLagMs }} ms</strong>
      </article>
      <article class="meta-card">
        <span>降级标记</span>
        <strong>{{ marketStore.wsDegraded ? '已降级' : '正常' }}</strong>
      </article>
      <article class="meta-card">
        <span>缓存新鲜度</span>
        <strong>{{ appStore.lastCacheStatus }}</strong>
      </article>
    </section>

    <el-alert class="market-alert" type="info" :closable="false" show-icon>
      <template #title>限流头信息</template>
      {{ rateLimitText }}
    </el-alert>

    <StockSearch @select="handleSelectStock" />

    <section v-loading="loadingOfficialBoard" class="market-board-grid">
      <article v-for="index in marketIndexes" :key="index.stockCode" class="market-board-card">
        <header>
          <h3>{{ index.stockName }}</h3>
          <small>{{ index.stockCode.toUpperCase() }}</small>
        </header>
        <strong>{{ formatPrice(index.currentPrice) }}</strong>
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

    <section v-loading="loadingOfficialBoard" class="market-rank-grid">
      <article class="market-rank-panel">
        <header>
          <h3>涨幅榜 TOP {{ RANK_LIMIT }}</h3>
        </header>
        <ul>
          <li v-for="item in riseRankList" :key="`rise-${item.stockCode}`" @click="handleSelectStock(item.stockCode)">
            <span>{{ item.stockCode.toUpperCase() }} {{ item.stockName }}</span>
            <b class="up">{{ formatPercent(item.changePercent) }}</b>
          </li>
        </ul>
      </article>

      <article class="market-rank-panel">
        <header>
          <h3>跌幅榜 TOP {{ RANK_LIMIT }}</h3>
        </header>
        <ul>
          <li v-for="item in fallRankList" :key="`fall-${item.stockCode}`" @click="handleSelectStock(item.stockCode)">
            <span>{{ item.stockCode.toUpperCase() }} {{ item.stockName }}</span>
            <b class="down">{{ formatPercent(item.changePercent) }}</b>
          </li>
        </ul>
      </article>
    </section>

    <MarketOverview :quotes="marketQuotes" />

    <section v-loading="quoteGridLoading" class="quote-grid">
      <QuoteCard
        v-for="quote in marketQuotes"
        :key="quote.stockCode"
        :quote="quote"
        @select="handleSelectStock"
      />
    </section>

    <section class="market-pagination">
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
      <small>榜单数据源：{{ boardDataSource === 'official' ? '官方源' : '官方源暂不可用（保留上次成功结果）' }}</small>
      <small>共 {{ totalPages }} 页，当前页股票会实时订阅，切页后自动切换订阅集合。</small>
    </section>
  </section>
</template>
