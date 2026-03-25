<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import KLineChart from '@/components/market/KLineChart.vue'
import QuoteCard from '@/components/market/QuoteCard.vue'
import { useMarketStore } from '@/stores/market'
import type { KLinePeriod } from '@/types/market'

const route = useRoute()
const marketStore = useMarketStore()
const period = ref<KLinePeriod>('DAILY')

const stockCode = computed(() => String(route.params.stockCode || '').toLowerCase())

async function loadDetail(code: string): Promise<void> {
  if (!code) {
    return
  }

  try {
    marketStore.setSelectedCode(code)
    await Promise.all([marketStore.loadQuote(code), marketStore.loadKLine(code, period.value)])
  } catch (error) {
    const message = error instanceof Error ? error.message : '加载详情失败'
    ElMessage.error(message)
  }
}

async function handlePeriodChange(value: KLinePeriod): Promise<void> {
  period.value = value
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
  await loadDetail(stockCode.value)
})
</script>

<template>
  <section class="stock-detail-page">
    <header class="detail-header">
      <h1>股票详情</h1>
      <p>实时成交价 + K 线技术视图，支持日/周/月切换。</p>
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
        <el-radio-group :model-value="period" @change="handlePeriodChange">
          <el-radio-button label="DAILY">日K</el-radio-button>
          <el-radio-button label="WEEKLY">周K</el-radio-button>
          <el-radio-button label="MONTHLY">月K</el-radio-button>
        </el-radio-group>
      </div>

      <KLineChart :points="marketStore.klinePoints" :loading="marketStore.loadingKLine" />
    </section>

    <section class="detail-placeholder">
      <h2>交易面板（下一迭代）</h2>
      <p>本迭代聚焦认证与行情能力，交易下单面板将在后续迭代接入。</p>
    </section>
  </section>
</template>
