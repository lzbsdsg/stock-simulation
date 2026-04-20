<script setup lang="ts">
import { computed, defineAsyncComponent, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import QuoteCard from '@/components/market/QuoteCard.vue'
import OrderForm from '@/components/trade/OrderForm.vue'
import OrderList from '@/components/trade/OrderList.vue'
import TradeHistory from '@/components/trade/TradeHistory.vue'
import { useMarketStore } from '@/stores/market'
import { useTradeStore } from '@/stores/trade'
import { useWatchlistStore } from '@/stores/watchlist'
import type { KLinePeriod } from '@/types/market'

type RangePreset = '3M' | '6M' | '1Y' | '3Y'

const DETAIL_QUOTE_POLL_MS = 5000
const DETAIL_KLINE_REFRESH_MS = 60000

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
const KLineChart = defineAsyncComponent(() => import('@/components/market/KLineChart.vue'))
const period = ref<KLinePeriod>('DAILY')
const rangePreset = ref<RangePreset>('1Y')
let refreshTimer: number | null = null
let marketRefreshTimer: number | null = null
let lastKlineRefreshAt = 0

const stockCode = computed(() => String(route.params.stockCode || '').toLowerCase())
const referenceClose = computed(() => marketStore.selectedQuote?.closePrice ?? null)
const isInWatchlist = computed(() => watchlistStore.items.some((item) => item.stockCode === stockCode.value))
const statusPillClass = computed(() => {
  if (marketStore.wsDegraded) {
    return 'pill-risk'
  }
  return 'pill-safe'
})

async function loadDetail(code: string): Promise<void> {
  if (!code) {
    return
  }

  try {
    marketStore.setSelectedCode(code)
    await Promise.all([
      marketStore.loadQuote(code, {
        preferCache: false,
        backgroundRefresh: false,
      }),
      marketStore.loadKLine(code, period.value, RANGE_DAYS_MAP[rangePreset.value], {
        preferCache: false,
        backgroundRefresh: false,
      }),
    ])
    lastKlineRefreshAt = Date.now()
  } catch (error) {
    const message = error instanceof Error ? error.message : '加载详情失败'
    ElMessage.error(message)
  }
}

async function handlePeriodChange(value: KLinePeriod): Promise<void> {
  period.value = value
  try {
    await marketStore.loadKLine(stockCode.value, period.value, RANGE_DAYS_MAP[rangePreset.value], {
      preferCache: false,
      backgroundRefresh: false,
    })
    lastKlineRefreshAt = Date.now()
  } catch (error) {
    const message = error instanceof Error ? error.message : '加载K线失败'
    ElMessage.error(message)
  }
}

async function handleRangeChange(value: RangePreset): Promise<void> {
  rangePreset.value = value
  try {
    await marketStore.loadKLine(stockCode.value, period.value, RANGE_DAYS_MAP[rangePreset.value], {
      preferCache: false,
      backgroundRefresh: false,
    })
    lastKlineRefreshAt = Date.now()
  } catch (error) {
    const message = error instanceof Error ? error.message : '加载K线失败'
    ElMessage.error(message)
  }
}

watch(
  () => stockCode.value,
  async (code) => {
    await loadDetail(code)
  },
)

onMounted(async () => {
  marketStore.connectRealtime()
  await loadDetail(stockCode.value)
  void Promise.all([tradeStore.loadOrders(), tradeStore.loadTrades()]).catch(() => undefined)
  void watchlistStore
    .load()
    .then(() => {
      marketStore.setWatchlistCodes(watchlistStore.items.map((item) => item.stockCode))
    })
    .catch(() => undefined)
  startAutoRefresh()
  startMarketRefresh()
})

onBeforeUnmount(() => {
  stopAutoRefresh()
  stopMarketRefresh()
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

function startMarketRefresh(): void {
  if (marketRefreshTimer !== null) {
    return
  }

  marketRefreshTimer = window.setInterval(() => {
    void refreshMarketPanels().catch(() => undefined)
  }, DETAIL_QUOTE_POLL_MS)
}

function stopMarketRefresh(): void {
  if (marketRefreshTimer === null) {
    return
  }
  window.clearInterval(marketRefreshTimer)
  marketRefreshTimer = null
}

async function refreshMarketPanels(): Promise<void> {
  const code = stockCode.value
  if (!code) {
    return
  }

  await marketStore.loadQuote(code, {
    preferCache: false,
    backgroundRefresh: false,
  })

  const now = Date.now()
  if (now - lastKlineRefreshAt < DETAIL_KLINE_REFRESH_MS) {
    return
  }

  await marketStore.loadKLine(code, period.value, RANGE_DAYS_MAP[rangePreset.value], {
    preferCache: false,
    backgroundRefresh: false,
  })
  lastKlineRefreshAt = now
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
    <header class="page-head">
      <div>
        <h1 class="page-title">股票详情</h1>
        <p class="page-subtitle">实时报价、技术图表与交易动作同屏联动。</p>
      </div>
      <el-button
        plain
        :disabled="isInWatchlist"
        @click="addCurrentStockToWatchlist"
      >
        {{ isInWatchlist ? '已在自选股' : '添加到自选股' }}
      </el-button>
    </header>

    <section class="kpi-strip">
      <span class="kpi-pill" :class="statusPillClass">
        链路状态
        <strong>{{ marketStore.wsStatus }}</strong>
      </span>
      <span class="kpi-pill pill-brand">
        推送延迟
        <strong class="mono-number">{{ marketStore.wsLagMs }} ms</strong>
      </span>
      <span class="kpi-pill" :class="isInWatchlist ? 'pill-safe' : 'pill-brand'">
        自选状态
        <strong>{{ isInWatchlist ? '已关注' : '未关注' }}</strong>
      </span>
    </section>

    <section class="detail-top-grid">
      <section class="section-card detail-quote-section">
        <div class="section-card-head">
          <div>
            <h2 class="section-card-title">实时行情</h2>
            <p class="section-card-subtitle">含开高低昨收与量价信息</p>
          </div>
        </div>

        <QuoteCard
          v-if="marketStore.selectedQuote"
          :quote="marketStore.selectedQuote"
          :clickable="false"
          class="detail-quote-card"
        />
      </section>

      <section class="section-card detail-status-section">
        <div class="section-card-head">
          <div>
            <h2 class="section-card-title">链路状态</h2>
            <p class="section-card-subtitle">实时推送可用性监控</p>
          </div>
          <span class="panel-tag">{{ marketStore.wsStatus }}</span>
        </div>

        <el-alert
          v-if="marketStore.wsDegraded"
          type="warning"
          :closable="false"
          title="检测到推送延迟超过5秒，页面已进入降级展示。"
          show-icon
        />
        <el-alert v-else type="success" :closable="false" title="实时链路正常，数据持续更新中。" show-icon />
      </section>
    </section>

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

<style scoped>
.detail-top-grid {
  display: grid;
  gap: 14px;
  grid-template-columns: minmax(0, 1.35fr) minmax(280px, 1fr);
  align-items: start;
}

.detail-quote-section,
.detail-status-section {
  align-content: start;
}

.detail-quote-card {
  max-width: none;
}

.detail-trade-grid {
  display: grid;
  gap: 14px;
}

@media (max-width: 1080px) {
  .detail-top-grid {
    grid-template-columns: 1fr;
  }
}
</style>
