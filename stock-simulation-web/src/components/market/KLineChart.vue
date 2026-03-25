<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import type { ECharts, EChartsOption } from 'echarts'
import type { KLinePoint } from '@/types/market'

interface SeriesParam {
  seriesName: string
  seriesType: string
  marker?: string
  axisValue?: string
  data?: unknown
  value?: unknown
}

const props = defineProps<{
  points: KLinePoint[]
  loading?: boolean
  referenceClose?: number | null
}>()

const MA_PERIODS = [5, 10, 20, 60]

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

function calcPriceBounds(candleData: number[][]): { min: number; max: number } {
  if (candleData.length === 0) {
    return { min: 0, max: 1 }
  }

  const lows = candleData.map((item) => item[2])
  const highs = candleData.map((item) => item[3])
  const rawMin = Math.min(...lows)
  const rawMax = Math.max(...highs)
  const baseRange = rawMax - rawMin

  const minRange = Math.max(rawMax * 0.02, 0.1)
  const effectiveRange = baseRange < minRange ? minRange : baseRange
  const padding = effectiveRange * 0.12

  return {
    min: Math.max(0.01, Number((rawMin - padding).toFixed(2))),
    max: Number((rawMax + padding).toFixed(2)),
  }
}

function formatVolume(value: number): string {
  if (value >= 100000000) {
    return `${(value / 100000000).toFixed(2)}亿`
  }
  if (value >= 10000) {
    return `${(value / 10000).toFixed(2)}万`
  }
  return `${Math.round(value)}`
}

function toNumber(value: unknown): number {
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : 0
  }
  if (typeof value === 'string' && value.trim() !== '') {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : 0
  }
  return 0
}

function formatTooltip(
  params: unknown,
  referenceClose: number,
): string {
  const list = Array.isArray(params) ? (params as SeriesParam[]) : [params as SeriesParam]
  if (list.length === 0) {
    return ''
  }

  const candle = list.find((item) => item.seriesType === 'candlestick')
  const volumeSeries = list.find((item) => item.seriesName === '成交量')

  const candleRaw =
    candle && Array.isArray(candle.data)
      ? (candle.data as number[])
      : candle && Array.isArray(candle.value)
        ? (candle.value as number[])
        : []

  const open = toNumber(candleRaw[0])
  const close = toNumber(candleRaw[1])
  const low = toNumber(candleRaw[2])
  const high = toNumber(candleRaw[3])
  const changePct = open === 0 ? 0 : ((close - open) / open) * 100
  const refPct = referenceClose > 0 ? ((close - referenceClose) / referenceClose) * 100 : 0

  const maLines = MA_PERIODS.map((period) => {
    const ma = list.find((item) => item.seriesName === `MA${period}`)
    const maValue = Array.isArray(ma?.value) ? toNumber((ma?.value as unknown[])[1]) : toNumber(ma?.value)
    return `<div>MA${period}: <b>${maValue > 0 ? maValue.toFixed(2) : '--'}</b></div>`
  }).join('')

  const volumeValue =
    volumeSeries && Array.isArray(volumeSeries.value)
      ? toNumber((volumeSeries.value as unknown[])[1])
      : toNumber(volumeSeries?.value)

  const date = list[0]?.axisValue ?? '--'

  return `
    <div style="min-width:190px;line-height:1.55;">
      <div style="font-weight:600;margin-bottom:4px;">${date}</div>
      <div>开: <b>${open.toFixed(2)}</b></div>
      <div>高: <b>${high.toFixed(2)}</b></div>
      <div>低: <b>${low.toFixed(2)}</b></div>
      <div>收: <b>${close.toFixed(2)}</b></div>
      <div>涨跌: <b style="color:${changePct >= 0 ? '#cf2b3c' : '#138a45'};">${changePct.toFixed(2)}%</b></div>
      <div>相对昨收: <b style="color:${refPct >= 0 ? '#cf2b3c' : '#138a45'};">${refPct.toFixed(2)}%</b></div>
      <div>成交量: <b>${formatVolume(volumeValue)}</b></div>
      ${maLines}
    </div>
  `
}

function buildOption(points: KLinePoint[], referenceCloseProp?: number | null): EChartsOption {
  const sorted = [...points]
    .filter((item) => item && item.open !== null && item.close !== null && item.high !== null && item.low !== null)
    .sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime())

  const categories = sorted.map((item) => item.date)
  const candleData = sorted.map((item) => [item.open, item.close, item.low, item.high])
  const closeData = sorted.map((item) => item.close)
  const priceBounds = calcPriceBounds(candleData)
  const referenceClose = referenceCloseProp ?? closeData[0] ?? 0
  const zoomStart =
    categories.length <= 120 ? 0 : Number((((categories.length - 120) / categories.length) * 100).toFixed(2))

  const volumeData = sorted.map((item) => ({
    value: item.volume,
    itemStyle: {
      color: item.close >= item.open ? '#cf2b3c' : '#138a45',
      opacity: 0.85,
    },
  }))

  return {
    animation: true,
    backgroundColor: '#ffffff',
    axisPointer: {
      link: [{ xAxisIndex: 'all' }],
      label: { backgroundColor: '#2f3f56' },
    },
    legend: {
      top: 2,
      left: 10,
      itemWidth: 14,
      itemHeight: 8,
      textStyle: { color: '#42556f', fontSize: 12 },
      data: ['K线', 'MA5', 'MA10', 'MA20', 'MA60', '成交量'],
    },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
      borderWidth: 1,
      borderColor: '#c7d4ea',
      backgroundColor: 'rgba(255,255,255,0.96)',
      textStyle: { color: '#10233c', fontSize: 12 },
      formatter: (params: unknown) => formatTooltip(params, referenceClose),
    },
    grid: [
      { left: '6%', right: '8%', top: 34, height: '58%' },
      { left: '6%', right: '8%', top: '75%', height: '16%' },
    ],
    xAxis: [
      {
        type: 'category',
        data: categories,
        boundaryGap: true,
        axisLine: { lineStyle: { color: '#cad5e6' } },
        axisTick: { show: false },
        axisLabel: {
          color: '#5e6f86',
          hideOverlap: true,
        },
        splitLine: {
          show: true,
          lineStyle: { color: '#edf2fb' },
        },
      },
      {
        type: 'category',
        gridIndex: 1,
        data: categories,
        boundaryGap: true,
        axisLine: { lineStyle: { color: '#cad5e6' } },
        axisTick: { show: false },
        axisLabel: {
          color: '#5e6f86',
          hideOverlap: true,
        },
        splitLine: {
          show: true,
          lineStyle: { color: '#edf2fb' },
        },
      },
    ],
    yAxis: [
      {
        position: 'left',
        scale: true,
        min: priceBounds.min,
        max: priceBounds.max,
        splitNumber: 5,
        axisLine: { lineStyle: { color: '#cad5e6' } },
        axisLabel: {
          color: '#5e6f86',
          formatter: (value: number) => value.toFixed(2),
        },
        splitLine: { lineStyle: { color: '#edf2fb' } },
      },
      {
        position: 'right',
        scale: true,
        min: priceBounds.min,
        max: priceBounds.max,
        splitNumber: 5,
        axisLine: { lineStyle: { color: '#cad5e6' } },
        axisLabel: {
          color: '#5e6f86',
          formatter: (value: number) => {
            if (referenceClose <= 0) {
              return '--'
            }
            return `${(((value - referenceClose) / referenceClose) * 100).toFixed(2)}%`
          },
        },
        splitLine: { show: false },
      },
      {
        gridIndex: 1,
        splitNumber: 2,
        axisLine: { lineStyle: { color: '#cad5e6' } },
        axisLabel: {
          color: '#5e6f86',
          formatter: (value: number) => formatVolume(value),
        },
        splitLine: { lineStyle: { color: '#edf2fb' } },
      },
    ],
    dataZoom: [
      {
        type: 'inside',
        xAxisIndex: [0, 1],
        start: zoomStart,
        end: 100,
      },
      {
        xAxisIndex: [0, 1],
        type: 'slider',
        top: '93%',
        height: 18,
        borderColor: '#d3deef',
        brushSelect: false,
        start: zoomStart,
        end: 100,
      },
    ],
    series: [
      {
        name: 'K线',
        type: 'candlestick',
        data: candleData,
        barWidth: '55%',
        itemStyle: {
          color: '#cf2b3c',
          color0: '#138a45',
          borderColor: '#cf2b3c',
          borderColor0: '#138a45',
        },
        markLine: {
          symbol: 'none',
          label: {
            formatter: '最新价',
            color: '#2f3f56',
          },
          lineStyle: {
            type: 'dashed',
            color: '#7087a7',
          },
          data:
            closeData.length > 0
              ? [
                  {
                    yAxis: closeData[closeData.length - 1],
                  },
                ]
              : [],
        },
      },
      ...MA_PERIODS.map((period, index) => ({
        name: `MA${period}`,
        type: 'line' as const,
        smooth: true,
        showSymbol: false,
        data: movingAverage(period, closeData),
        lineStyle: {
          width: 1.25,
          color: ['#2f7fff', '#f0a53a', '#8c64ff', '#00a8a8'][index],
        },
        emphasis: { disabled: true },
      })),
      {
        name: '成交量',
        type: 'bar' as const,
        xAxisIndex: 1,
        yAxisIndex: 2,
        data: volumeData,
      },
    ],
  }
}

function renderChart(points: KLinePoint[]): void {
  if (!chart) {
    return
  }

  chart.setOption(buildOption(points, props.referenceClose), true)
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
  () => [props.points, props.referenceClose],
  () => {
    renderChart(props.points)
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
