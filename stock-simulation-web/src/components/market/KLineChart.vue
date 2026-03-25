<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import type { ECharts, EChartsOption } from 'echarts'
import type { KLinePoint } from '@/types/market'

const props = defineProps<{
  points: KLinePoint[]
  loading?: boolean
}>()

const chartRef = ref<HTMLDivElement | null>(null)
let chart: ECharts | null = null

function movingAverage(period: number, values: number[]): Array<number | '-'> {
  return values.map((_, index) => {
    if (index < period - 1) {
      return '-'
    }

    const windowSlice = values.slice(index - period + 1, index + 1)
    const sum = windowSlice.reduce((total, item) => total + item, 0)
    return Number((sum / period).toFixed(2))
  })
}

function buildOption(points: KLinePoint[]): EChartsOption {
  const sorted = [...points].sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime())
  const categories = sorted.map((item) => item.date)
  const kData = sorted.map((item) => [item.open, item.close, item.low, item.high])
  const closeData = sorted.map((item) => item.close)
  const volumeData = sorted.map((item, index) => ({
    value: item.volume,
    itemStyle: {
      color: item.close >= item.open ? '#ee4d4f' : '#1c9f5a',
    },
    xAxisIndex: index,
  }))

  return {
    animation: true,
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'cross',
      },
    },
    legend: {
      top: 8,
      data: ['K线', 'MA5', 'MA10', '成交量'],
    },
    grid: [
      { left: '7%', right: '4%', top: 48, height: '56%' },
      { left: '7%', right: '4%', top: '72%', height: '18%' },
    ],
    xAxis: [
      {
        type: 'category',
        data: categories,
        boundaryGap: true,
        axisLine: { lineStyle: { color: '#d4d8e5' } },
        min: 'dataMin',
        max: 'dataMax',
      },
      {
        type: 'category',
        gridIndex: 1,
        data: categories,
        boundaryGap: true,
        axisTick: { show: false },
        axisLine: { lineStyle: { color: '#d4d8e5' } },
      },
    ],
    yAxis: [
      {
        scale: true,
        splitArea: { show: true },
        axisLine: { lineStyle: { color: '#d4d8e5' } },
      },
      {
        gridIndex: 1,
        splitNumber: 2,
        axisLine: { lineStyle: { color: '#d4d8e5' } },
      },
    ],
    dataZoom: [
      {
        type: 'inside',
        xAxisIndex: [0, 1],
        start: 70,
        end: 100,
      },
      {
        show: true,
        xAxisIndex: [0, 1],
        type: 'slider',
        top: '93%',
        start: 70,
        end: 100,
      },
    ],
    series: [
      {
        name: 'K线',
        type: 'candlestick',
        data: kData,
        itemStyle: {
          color: '#ee4d4f',
          color0: '#1c9f5a',
          borderColor: '#ee4d4f',
          borderColor0: '#1c9f5a',
        },
      },
      {
        name: 'MA5',
        type: 'line',
        smooth: true,
        showSymbol: false,
        data: movingAverage(5, closeData),
        lineStyle: { width: 1.4, color: '#4f7cff' },
      },
      {
        name: 'MA10',
        type: 'line',
        smooth: true,
        showSymbol: false,
        data: movingAverage(10, closeData),
        lineStyle: { width: 1.4, color: '#f5a623' },
      },
      {
        name: '成交量',
        type: 'bar',
        xAxisIndex: 1,
        yAxisIndex: 1,
        data: volumeData,
      },
    ],
  }
}

function renderChart(points: KLinePoint[]): void {
  if (!chart) {
    return
  }

  chart.setOption(buildOption(points), true)
}

function handleResize(): void {
  chart?.resize()
}

onMounted(async () => {
  await nextTick()
  if (!chartRef.value) {
    return
  }

  chart = echarts.init(chartRef.value)
  renderChart(props.points)
  window.addEventListener('resize', handleResize)
})

watch(
  () => props.points,
  (points) => {
    renderChart(points)
  },
  { deep: true },
)

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div v-loading="loading" class="kline-chart">
    <div ref="chartRef" class="kline-canvas" />
  </div>
</template>
