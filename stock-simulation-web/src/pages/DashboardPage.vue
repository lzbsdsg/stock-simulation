<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useMarketStore } from '@/stores/market'
import { useAppStore } from '@/stores/app'
import { usePortfolioStore } from '@/stores/portfolio'
import { formatPercent, formatPrice, percentClass } from '@/utils/format'

const router = useRouter()
const marketStore = useMarketStore()
const appStore = useAppStore()
const portfolioStore = usePortfolioStore()

onMounted(async () => {
  if (marketStore.watchQuotes.length === 0) {
    await marketStore.initializeMarket()
  } else {
    marketStore.connectRealtime()
  }

  if (!portfolioStore.overview) {
    await portfolioStore.loadOverview()
  }
})

function openMarket() {
  router.push('/market')
}
</script>

<template>
  <section class="dashboard-page">
    <header class="dashboard-head">
      <h1>交易日驾驶舱</h1>
      <p>实时行情与缓存新鲜度概览，支持跳转到股票详情页查看 K 线。</p>
    </header>

    <div class="dashboard-grid">
      <article class="metric-card">
        <span class="metric-label">实时连接状态</span>
        <strong>{{ marketStore.wsStatus }}</strong>
      </article>
      <article class="metric-card">
        <span class="metric-label">推送延迟</span>
        <strong>{{ marketStore.wsLagMs }} ms</strong>
      </article>
      <article class="metric-card">
        <span class="metric-label">缓存状态</span>
        <strong>{{ appStore.lastCacheStatus }}</strong>
      </article>
      <article class="metric-card">
        <span class="metric-label">热门股票平均涨跌</span>
        <strong>
          {{
            formatPercent(
              marketStore.watchQuotes.reduce((sum, item) => sum + (item.changePercent ?? 0), 0) /
                Math.max(1, marketStore.watchQuotes.length),
            )
          }}
        </strong>
      </article>
      <article class="metric-card">
        <span class="metric-label">资产总额</span>
        <strong>{{ formatPrice(portfolioStore.overview?.totalAssets) }}</strong>
      </article>
      <article class="metric-card">
        <span class="metric-label">今日盈亏率</span>
        <strong :class="percentClass(portfolioStore.overview?.todayProfitRate)">
          {{ formatPercent(portfolioStore.overview?.todayProfitRate) }}
        </strong>
      </article>
    </div>

    <section class="dashboard-list">
      <h2>关注股票</h2>
      <ul>
        <li
          v-for="quote in marketStore.watchQuotes"
          :key="quote.stockCode"
          class="dashboard-list-item"
          @click="router.push(`/market/${quote.stockCode}`)"
        >
          <span>{{ quote.stockCode.toUpperCase() }} {{ quote.stockName }}</span>
          <span
            :class="[
              'delta',
              quote.changePercent === null ? 'flat' : quote.changePercent > 0 ? 'up' : quote.changePercent < 0 ? 'down' : 'flat',
            ]"
          >
            {{ formatPercent(quote.changePercent) }}
          </span>
        </li>
      </ul>
    </section>

    <div class="dashboard-actions">
      <el-button type="primary" @click="openMarket">进入行情中心</el-button>
    </div>
  </section>
</template>
