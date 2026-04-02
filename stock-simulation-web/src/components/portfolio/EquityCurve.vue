<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import type { EquityCurve } from '@/types/portfolio'

const props = defineProps<{
  curve: EquityCurve | null
}>()

const chartEl = ref<HTMLElement | null>(null)
let chart: echarts.ECharts | null = null

function renderChart(): void {
  if (!chartEl.value || !props.curve) {
    return
  }

  const dates = props.curve.points.map((item) => item.date)
  const assets = props.curve.points.map((item) => item.totalAssets)
  const profitRates = props.curve.points.map((item) => item.profitRate)

  if (!chart) {
    chart = echarts.init(chartEl.value)
  }

  chart.setOption({
    tooltip: {
      trigger: 'axis',
    },
    legend: {
      data: ['总资产', '收益率%'],
    },
    xAxis: {
      type: 'category',
      data: dates,
    },
    yAxis: [
      { type: 'value', name: '资产' },
      { type: 'value', name: '收益率%', position: 'right' },
    ],
    series: [
      {
        name: '总资产',
        type: 'line',
        smooth: true,
        data: assets,
      },
      {
        name: '收益率%',
        type: 'line',
        smooth: true,
        yAxisIndex: 1,
        data: profitRates,
      },
    ],
    grid: {
      left: 36,
      right: 42,
      top: 28,
      bottom: 36,
    },
  })
}

onMounted(async () => {
  await nextTick()
  renderChart()
  window.addEventListener('resize', resizeChart)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeChart)
  chart?.dispose()
  chart = null
})

watch(
  () => props.curve,
  async () => {
    await nextTick()
    renderChart()
  },
  { deep: true },
)

function resizeChart(): void {
  chart?.resize()
}
</script>

<template>
  <section class="equity-curve-panel">
    <h3>收益曲线</h3>
    <div v-if="!curve || curve.points.length === 0" class="chart-empty">暂无收益曲线数据</div>
    <div v-else ref="chartEl" class="equity-chart"></div>
  </section>
</template>
