<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import StockSearch from '@/components/market/StockSearch.vue'
import QuoteCard from '@/components/market/QuoteCard.vue'
import MarketOverview from '@/components/market/MarketOverview.vue'
import { useAppStore } from '@/stores/app'
import { useMarketStore } from '@/stores/market'

const router = useRouter()
const marketStore = useMarketStore()
const appStore = useAppStore()

const rateLimitText = computed(() => {
  const info = appStore.lastRateLimit
  return `limit=${info.limit ?? '-'} | remaining=${info.remaining ?? '-'} | reset=${info.reset ?? '-'}`
})

onMounted(async () => {
  try {
    await marketStore.initializeMarket()
  } catch (error) {
    const message = error instanceof Error ? error.message : '行情初始化失败'
    ElMessage.error(message)
  }
})

function handleSelectStock(stockCode: string): void {
  router.push(`/market/${stockCode}`)
}

async function handleRefresh(): Promise<void> {
  try {
    await marketStore.loadWatchQuotes()
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

    <MarketOverview :quotes="marketStore.watchQuotes" />

    <section v-loading="marketStore.loadingQuotes" class="quote-grid">
      <QuoteCard
        v-for="quote in marketStore.watchQuotes"
        :key="quote.stockCode"
        :quote="quote"
        @select="handleSelectStock"
      />
    </section>
  </section>
</template>
