<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useMarketStore } from '@/stores/market'
import { useAppStore } from '@/stores/app'
import { usePortfolioStore } from '@/stores/portfolio'
import { useWatchlistStore } from '@/stores/watchlist'
import { formatPercent, formatPrice } from '@/utils/format'

const router = useRouter()
const marketStore = useMarketStore()
const appStore = useAppStore()
const portfolioStore = usePortfolioStore()
const watchlistStore = useWatchlistStore()

const focusQuotes = computed(() => {
  const focusCodes = watchlistStore.items.map((item) => item.stockCode.toLowerCase())
  return focusCodes
    .map((code) => marketStore.quoteMap[code])
    .filter((quote) => Boolean(quote))
})

const avgFocusChangePercent = computed(() => {
  if (focusQuotes.value.length === 0) {
    return null
  }
  const total = focusQuotes.value.reduce((sum, item) => sum + (item.changePercent ?? 0), 0)
  return total / focusQuotes.value.length
})

const strongestQuote = computed(() => {
  if (focusQuotes.value.length === 0) {
    return null
  }
  return [...focusQuotes.value].sort((a, b) => (b.changePercent ?? -Infinity) - (a.changePercent ?? -Infinity))[0]
})

const weakestQuote = computed(() => {
  if (focusQuotes.value.length === 0) {
    return null
  }
  return [...focusQuotes.value].sort((a, b) => (a.changePercent ?? Infinity) - (b.changePercent ?? Infinity))[0]
})

const cacheToneClass = computed(() => {
  const cacheStatus = (appStore.lastCacheStatus || '').toUpperCase()
  if (cacheStatus.includes('HIT')) {
    return 'pill-safe'
  }
  if (cacheStatus.includes('MISS')) {
    return 'pill-risk'
  }
  return 'pill-brand'
})

const wsStatusClass = computed(() => (marketStore.wsStatus === 'CONNECTED' ? 'up' : 'down'))
const lagStatusClass = computed(() => {
  if (marketStore.wsLagMs > 5000) {
    return 'down'
  }
  if (marketStore.wsLagMs > 1500) {
    return 'flat'
  }
  return 'up'
})

onMounted(async () => {
  try {
    await watchlistStore.load()
    const focusCodes = watchlistStore.items.map((item) => item.stockCode)
    marketStore.setWatchlistCodes(focusCodes)

    if (marketStore.realtimeCodes.length === 0) {
      await marketStore.initializeMarket()
    } else {
      marketStore.connectRealtime()
    }

    await marketStore.loadWatchlistQuotes()

    if (!portfolioStore.overview) {
      await portfolioStore.loadOverview()
    }
  } catch (error) {
    const message = error instanceof Error ? error.message : '仪表盘初始化失败，请稍后重试'
    ElMessage.warning(message)
  }
})

function openMarket() {
  router.push('/market')
}

function openTrade() {
  router.push('/trade')
}

function openPortfolio() {
  router.push('/portfolio')
}

function quotePulseText(code?: string, changePercent?: number | null): string {
  if (!code || changePercent == null) {
    return '--'
  }
  return `${code.toUpperCase()} ${formatPercent(changePercent)}`
}

// 提取涨跌幅样式为纯净的 class 属性
function getChangeClass(percent: number | null | undefined): string {
  if (percent == null) return 'text-secondary'
  return percent > 0 ? 'text-rise' : percent < 0 ? 'text-fall' : 'text-secondary'
}
</script>

<template>
  <div class="dashboard-page">
    <header class="page-head">
      <div>
        <h1 class="page-title">交易总览工作台</h1>
        <p class="page-subtitle">资产、行情与执行状态的统一视图，适合快速决策与盘中扫描。</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" @click="openMarket">查看行情</el-button>
        <el-button plain @click="openTrade">发起交易</el-button>
      </div>
    </header>

    <section class="kpi-strip dashboard-kpi-strip">
      <span class="kpi-pill pill-brand">
        组合均幅
        <strong :class="getChangeClass(avgFocusChangePercent)">{{ formatPercent(avgFocusChangePercent) }}</strong>
      </span>
      <span class="kpi-pill pill-safe">
        最强标的
        <strong class="mono-number" :class="getChangeClass(strongestQuote?.changePercent)">
          {{ quotePulseText(strongestQuote?.stockCode, strongestQuote?.changePercent) }}
        </strong>
      </span>
      <span class="kpi-pill pill-risk">
        弱势标的
        <strong class="mono-number" :class="getChangeClass(weakestQuote?.changePercent)">
          {{ quotePulseText(weakestQuote?.stockCode, weakestQuote?.changePercent) }}
        </strong>
      </span>
      <span class="kpi-pill" :class="cacheToneClass">
        缓存态
        <strong>{{ appStore.lastCacheStatus || 'N/A' }}</strong>
      </span>
    </section>

    <section class="dashboard-command-grid">
      <article class="capital-hero">
        <div class="capital-head">
          <span>账户总资产</span>
          <small class="panel-tag">实时估值</small>
        </div>
        <strong class="capital-value mono-number">{{ formatPrice(portfolioStore.overview?.totalAssets) }}</strong>
        <div class="capital-foot">
          <div class="capital-foot-item">
            <span>可用资金</span>
            <strong class="mono-number">{{ formatPrice(portfolioStore.overview?.availableBalance) }}</strong>
          </div>
          <div class="capital-foot-item">
            <span>持仓市值</span>
            <strong class="mono-number">{{ formatPrice(portfolioStore.overview?.marketValue) }}</strong>
          </div>
          <div class="capital-foot-item">
            <span>当日盈亏率</span>
            <strong :class="getChangeClass(portfolioStore.overview?.todayProfitRate)">
              {{ formatPercent(portfolioStore.overview?.todayProfitRate) }}
            </strong>
          </div>
        </div>
      </article>

      <aside class="section-card pulse-panel">
        <div class="section-card-head">
          <div>
            <h2 class="section-card-title">盘面脉冲</h2>
            <p class="section-card-subtitle">聚焦你关注的标的表现</p>
          </div>
        </div>
        <div class="metric-list">
          <article class="metric-tile">
            <span class="metric-label">自选股均幅</span>
            <strong class="metric-value" :class="getChangeClass(avgFocusChangePercent)">
              {{ formatPercent(avgFocusChangePercent) }}
            </strong>
          </article>
          <article class="metric-tile">
            <span class="metric-label">跟踪数量</span>
            <strong class="metric-value mono-number">{{ focusQuotes.length }}</strong>
          </article>
          <article class="metric-tile">
            <span class="metric-label">缓存状态</span>
            <strong class="metric-value">{{ appStore.lastCacheStatus || 'N/A' }}</strong>
          </article>
        </div>
      </aside>
    </section>

    <section class="dashboard-lower-grid">
      <section class="section-card focus-board">
        <div class="section-card-head">
          <div>
            <h2 class="section-card-title">自选动态</h2>
            <p class="section-card-subtitle">代码、最新价与涨跌幅实时联动</p>
          </div>
          <el-button link type="primary" @click="openMarket">管理自选</el-button>
        </div>

        <div v-if="focusQuotes.length > 0" class="focus-head">
          <span>标的</span>
          <span>最新价</span>
          <span>涨跌幅</span>
        </div>

        <ul v-if="focusQuotes.length > 0" class="watchlist">
          <li
            v-for="quote in focusQuotes"
            :key="quote.stockCode"
            class="watchlist-item"
            @click="router.push(`/market/${quote.stockCode}`)"
          >
            <div class="stock-info">
              <span class="stock-name">{{ quote.stockName }}</span>
              <span class="stock-code">{{ quote.stockCode.toUpperCase() }}</span>
            </div>
            <div class="stock-price" :class="getChangeClass(quote.changePercent)">
              <span class="mono-number">{{ formatPrice(quote.currentPrice) }}</span>
            </div>
            <div class="stock-change" :class="getChangeClass(quote.changePercent)">
              {{ formatPercent(quote.changePercent) }}
            </div>
          </li>
        </ul>
        <el-empty v-else description="暂无自选股，进入行情中心添加" :image-size="80" />
      </section>

      <aside class="section-card status-board">
        <div class="section-card-head">
          <div>
            <h2 class="section-card-title">执行与连接状态</h2>
            <p class="section-card-subtitle">链路健康度直接影响下单和行情体验</p>
          </div>
        </div>

        <div class="status-body">
          <div class="status-item">
            <span class="status-label">实时连接</span>
            <span class="status-value" :class="wsStatusClass">
              <span class="status-dot" :class="{ active: marketStore.wsStatus === 'CONNECTED' }"></span>
              {{ marketStore.wsStatus }}
            </span>
          </div>
          <div class="status-item">
            <span class="status-label">行情延迟</span>
            <span class="status-value" :class="lagStatusClass">{{ marketStore.wsLagMs }} ms</span>
          </div>
          <div class="status-item">
            <span class="status-label">热数据缓存</span>
            <span class="status-value">{{ appStore.lastCacheStatus || 'N/A' }}</span>
          </div>
        </div>

        <div class="status-actions">
          <el-button plain @click="openPortfolio">查看持仓</el-button>
          <el-button plain @click="openTrade">查看委托</el-button>
        </div>
      </aside>
    </section>
  </div>
</template>

<style scoped>
.dashboard-page {
  display: grid;
  gap: 18px;
}

.dashboard-kpi-strip {
  margin-top: -4px;
}

.dashboard-command-grid {
  display: grid;
  gap: 14px;
  grid-template-columns: minmax(0, 1.6fr) minmax(300px, 1fr);
}

.capital-hero {
  border: 1px solid #8ba8c3;
  border-radius: 16px;
  background: linear-gradient(132deg, #193a59 0%, #244f74 62%, #315f84 100%);
  box-shadow: var(--shadow-sm);
  color: #fff;
  padding: 22px;
  display: grid;
  gap: 14px;
}

.capital-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  font-size: 14px;
}

.capital-head .panel-tag {
  background: rgba(255, 255, 255, 0.08);
  border-color: rgba(255, 255, 255, 0.24);
  color: #dce7f3;
}

.capital-value {
  font-size: clamp(30px, 3vw, 44px);
  line-height: 1;
  font-weight: 700;
}

.capital-foot {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.capital-foot-item {
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.2);
  padding: 10px 12px;
  display: grid;
  gap: 3px;
}

.capital-foot-item span {
  font-size: 12px;
  color: #deebf8;
}

.capital-foot-item strong {
  font-size: 20px;
  font-weight: 700;
  color: #fff;
}

.pulse-panel {
  align-content: start;
}

.dashboard-lower-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(300px, 1fr);
  gap: 14px;
  align-items: start;
}

.focus-board {
  padding-bottom: 10px;
}

.focus-head {
  display: flex;
  justify-content: space-between;
  color: var(--text-tertiary);
  font-size: 12px;
  letter-spacing: 0.2px;
  padding: 0 14px 8px;
}

.focus-head span:nth-child(1) {
  flex: 1;
}

.focus-head span:nth-child(2),
.focus-head span:nth-child(3) {
  width: 110px;
  text-align: right;
}

.watchlist {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
}

.watchlist-item {
  display: flex;
  align-items: center;
  gap: 12px;
  border-top: 1px solid var(--line-default);
  padding: 11px 14px;
  cursor: pointer;
  transition: background-color 0.2s ease;
}

.watchlist-item:hover {
  background: var(--bg-panel-muted);
}

.stock-info {
  flex: 1;
  min-width: 0;
  display: grid;
}

.stock-name {
  font-size: 14px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stock-code {
  font-size: 12px;
  color: var(--text-tertiary);
}

.stock-price,
.stock-change {
  width: 110px;
  text-align: right;
  font-weight: 700;
}

.status-board {
  align-content: start;
}

.status-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.status-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border: 1px solid var(--line-default);
  border-radius: 10px;
  padding: 10px 12px;
  background: var(--bg-panel-muted);
}

.status-label {
  color: var(--text-secondary);
  font-size: 13px;
}

.status-value {
  font-size: 14px;
  font-weight: 700;
  display: flex;
  align-items: center;
  gap: 7px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: var(--text-tertiary);
}

.status-dot.active {
  background: var(--fall);
}

.status-actions {
  display: flex;
  gap: 8px;
}

@media (max-width: 1024px) {
  .dashboard-command-grid,
  .dashboard-lower-grid {
    grid-template-columns: 1fr;
  }

  .capital-foot {
    grid-template-columns: 1fr;
  }

  .focus-head {
    display: none;
  }

  .stock-price,
  .stock-change {
    width: 86px;
  }
}

@media (max-width: 720px) {
  .status-actions,
  .header-actions {
    width: 100%;
  }

  .status-actions :deep(.el-button),
  .header-actions :deep(.el-button) {
    flex: 1;
  }
}
</style>
