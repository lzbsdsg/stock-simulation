<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import AssetOverview from '@/components/portfolio/AssetOverview.vue'
import PositionTable from '@/components/portfolio/PositionTable.vue'
import EquityCurve from '@/components/portfolio/EquityCurve.vue'
import FundFlowTable from '@/components/portfolio/FundFlowTable.vue'
import { usePortfolioStore } from '@/stores/portfolio'
import { formatPercent, formatPrice } from '@/utils/format'

const portfolioStore = usePortfolioStore()
const days = ref(30)

const exposureRate = computed(() => {
  const overview = portfolioStore.overview
  if (!overview || !overview.totalAssets) {
    return null
  }
  return (overview.marketValue / overview.totalAssets) * 100
})

const cashRate = computed(() => {
  const overview = portfolioStore.overview
  if (!overview || !overview.totalAssets) {
    return null
  }
  return (overview.availableBalance / overview.totalAssets) * 100
})

onMounted(async () => {
  try {
    await portfolioStore.refreshAll(days.value)
  } catch (error) {
    const message = error instanceof Error ? error.message : '持仓数据加载失败'
    ElMessage.error(message)
  }
})

watch(days, async (value) => {
  await portfolioStore.loadEquityCurve(value)
})

async function refreshAll(): Promise<void> {
  await portfolioStore.refreshAll(days.value)
}
</script>

<template>
  <section class="portfolio-page">
    <header class="page-head">
      <div>
        <h1 class="page-title">持仓与资产</h1>
        <p class="page-subtitle">围绕成本、市值、盈亏和资金流的立体视图。</p>
      </div>
      <el-button plain @click="refreshAll">刷新</el-button>
    </header>

    <section class="kpi-strip">
      <span class="kpi-pill pill-brand">
        持仓暴露度
        <strong>{{ formatPercent(exposureRate) }}</strong>
      </span>
      <span class="kpi-pill pill-safe">
        现金占比
        <strong>{{ formatPercent(cashRate) }}</strong>
      </span>
      <span class="kpi-pill pill-risk">
        今日盈亏
        <strong class="mono-number" :class="(portfolioStore.overview?.todayProfit ?? 0) >= 0 ? 'up' : 'down'">
          {{ formatPrice(portfolioStore.overview?.todayProfit) }}
        </strong>
      </span>
    </section>

    <section class="section-card">
      <div class="section-card-head">
        <div>
          <h2 class="section-card-title">账户资产摘要</h2>
          <p class="section-card-subtitle">总资产、可用资金与收益率快速浏览</p>
        </div>
      </div>
      <AssetOverview />
    </section>

    <section class="portfolio-main-grid">
      <div class="portfolio-main-left">
        <PositionTable />
        <FundFlowTable />
      </div>

      <aside class="portfolio-main-right">
        <section class="section-card curve-toolbar-card">
          <div class="section-card-head">
            <div>
              <h2 class="section-card-title">收益曲线区间</h2>
              <p class="section-card-subtitle">按时间窗口观察收益波动</p>
            </div>
          </div>
          <section class="curve-toolbar">
            <span>区间：</span>
            <el-radio-group v-model="days" size="small">
              <el-radio-button :label="30">30天</el-radio-button>
              <el-radio-button :label="90">90天</el-radio-button>
              <el-radio-button :label="180">180天</el-radio-button>
            </el-radio-group>
          </section>
          <EquityCurve :curve="portfolioStore.equityCurve" />
        </section>
      </aside>
    </section>
  </section>
</template>

<style scoped>
.portfolio-main-grid {
  display: grid;
  gap: 14px;
  grid-template-columns: minmax(0, 1.35fr) minmax(320px, 1fr);
  align-items: start;
}

.portfolio-main-left,
.portfolio-main-right {
  display: grid;
  gap: 14px;
}

.curve-toolbar-card {
  align-content: start;
}

.curve-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
}

.curve-toolbar span {
  color: var(--text-secondary);
  font-size: 13px;
}

@media (max-width: 1140px) {
  .portfolio-main-grid {
    grid-template-columns: 1fr;
  }
}
</style>
