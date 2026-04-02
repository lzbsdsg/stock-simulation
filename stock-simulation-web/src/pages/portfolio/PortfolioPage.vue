<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import AssetOverview from '@/components/portfolio/AssetOverview.vue'
import PositionTable from '@/components/portfolio/PositionTable.vue'
import EquityCurve from '@/components/portfolio/EquityCurve.vue'
import FundFlowTable from '@/components/portfolio/FundFlowTable.vue'
import { usePortfolioStore } from '@/stores/portfolio'

const portfolioStore = usePortfolioStore()
const days = ref(30)

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
    <header class="portfolio-header">
      <div>
        <h1>持仓与资产</h1>
        <p>展示资产概览、实时持仓盈亏与收益曲线。</p>
      </div>
      <el-button type="primary" plain @click="refreshAll">刷新</el-button>
    </header>

    <AssetOverview />
    <PositionTable />

    <section class="curve-toolbar">
      <span>收益区间：</span>
      <el-radio-group v-model="days" size="small">
        <el-radio-button :label="30">30天</el-radio-button>
        <el-radio-button :label="90">90天</el-radio-button>
        <el-radio-button :label="180">180天</el-radio-button>
      </el-radio-group>
    </section>

    <EquityCurve :curve="portfolioStore.equityCurve" />
    <FundFlowTable />
  </section>
</template>
