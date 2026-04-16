<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import KLineChart from '@/components/market/KLineChart.vue'
import QuoteCard from '@/components/market/QuoteCard.vue'
import OrderForm from '@/components/trade/OrderForm.vue'
import OrderList from '@/components/trade/OrderList.vue'
import TradeHistory from '@/components/trade/TradeHistory.vue'
import { useMarketStore } from '@/stores/market'
import { useTradeStore } from '@/stores/trade'
import { useWatchlistStore } from '@/stores/watchlist'
import type { KLinePeriod } from '@/types/market'

type RangePreset = '3M' | '6M' | '1Y' | '3Y'

const RANGE_DAYS_MAP: Record<RangePreset, number> = {
  '3M': 90,
  '6M': 180,
  '1Y': 365,
  '3Y': 365 * 3,
}

const route = useRoute()
const marketStore = useMarketStore()
const tradeStore = useTradeStore()
const watchlistStore = useWatchlistStore()
const period = ref<KLinePeriod>('DAILY')
const rangePreset = ref<RangePreset>('1Y')
let refreshTimer: number | null = null

const stockCode = computed(() => String(route.params.stockCode || '').toLowerCase())
const referenceClose = computed(() => marketStore.selectedQuote?.closePrice ?? null)
const isInWatchlist = computed(() => watchlistStore.items.some((item) => item.stockCode === stockCode.value))

async function loadDetail(code: string): Promise<void> {
  if (!code) {
    return
  }

  try {
    marketStore.setSelectedCode(code)
    await Promise.all([
      marketStore.loadQuote(code),
      marketStore.loadKLine(code, period.value, RANGE_DAYS_MAP[rangePreset.value]),
    ])
  } catch (error) {
    const message = error instanceof Error ? error.message : '加载详情失败'
    ElMessage.error(message)
  }
}

async function handlePeriodChange(value: KLinePeriod): Promise<void> {
  period.value = value
  await loadDetail(stockCode.value)
}

async function handleRangeChange(value: RangePreset): Promise<void> {
  rangePreset.value = value
  await loadDetail(stockCode.value)
}

watch(
  () => stockCode.value,
  async (code) => {
    await loadDetail(code)
  },
)

onMounted(async () => {
  marketStore.connectRealtime()
  await Promise.all([
    loadDetail(stockCode.value),
    tradeStore.loadOrders(),
    tradeStore.loadTrades(),
    watchlistStore.load(),
  ])
  marketStore.setWatchlistCodes(watchlistStore.items.map((item) => item.stockCode))
  startAutoRefresh()
})

onBeforeUnmount(() => {
  stopAutoRefresh()
})

function startAutoRefresh(): void {
  if (refreshTimer !== null) {
    return
  }
  refreshTimer = window.setInterval(() => {
    if (tradeStore.loadingOrders || tradeStore.loadingTrades || tradeStore.placingOrder) {
      return
    }
    void refreshTradePanels().catch(() => undefined)
  }, 3000)
}

function stopAutoRefresh(): void {
  if (refreshTimer === null) {
    return
  }
  window.clearInterval(refreshTimer)
  refreshTimer = null
}

async function refreshTradePanels(): Promise<void> {
  await Promise.all([tradeStore.loadOrders(), tradeStore.loadTrades()])
}

async function addCurrentStockToWatchlist(): Promise<void> {
  if (!stockCode.value || isInWatchlist.value) {
    return
  }
  try {
    await watchlistStore.add(stockCode.value)
    marketStore.setWatchlistCodes(watchlistStore.items.map((item) => item.stockCode))
    ElMessage.success('已添加到自选股')
  } catch (error) {
    const message = error instanceof Error ? error.message : '添加自选股失败'
    ElMessage.error(message)
  }
}
</script>

<template>
  <section class="stock-detail-page">
    <header class="detail-header">
      <div>
        <h1>股票详情</h1>
        <p>实时成交价 + K 线技术视图，支持日/周/月切换。</p>
      </div>
      <el-button
        type="primary"
        plain
        :disabled="isInWatchlist"
        @click="addCurrentStockToWatchlist"
      >
        {{ isInWatchlist ? '已在自选股' : '添加到自选股' }}
      </el-button>
    </header>

    <QuoteCard
      v-if="marketStore.selectedQuote"
      :quote="marketStore.selectedQuote"
      :clickable="false"
      class="detail-quote-card"
    />

    <el-alert
      v-if="marketStore.wsDegraded"
      type="warning"
      :closable="false"
      title="检测到推送延迟超过5秒，页面已进入降级展示。"
      show-icon
    />

    <section class="detail-panel">
      <div class="kline-toolbar">
        <div class="kline-toolbar-group">
          <span class="kline-toolbar-label">周期</span>
          <el-radio-group :model-value="period" @change="handlePeriodChange">
            <el-radio-button label="DAILY">日K</el-radio-button>
            <el-radio-button label="WEEKLY">周K</el-radio-button>
            <el-radio-button label="MONTHLY">月K</el-radio-button>
          </el-radio-group>
        </div>

        <div class="kline-toolbar-group">
          <span class="kline-toolbar-label">区间</span>
          <el-radio-group :model-value="rangePreset" @change="handleRangeChange">
            <el-radio-button label="3M">3月</el-radio-button>
            <el-radio-button label="6M">6月</el-radio-button>
            <el-radio-button label="1Y">1年</el-radio-button>
            <el-radio-button label="3Y">3年</el-radio-button>
          </el-radio-group>
        </div>
      </div>

      <KLineChart
        :points="marketStore.klinePoints"
        :loading="marketStore.loadingKLine"
        :reference-close="referenceClose"
      />
    </section>

    <section class="detail-trade-grid">
      <OrderForm :stock-code="stockCode" @placed="refreshTradePanels" />
      <OrderList />
      <TradeHistory />
    </section>
  </section>
</template>
