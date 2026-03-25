<script setup lang="ts">
import { computed } from 'vue'
import type { Quote } from '@/types/market'
import { formatPercent, formatVolume } from '@/utils/format'

const props = defineProps<{
  quotes: Quote[]
}>()

const riseCount = computed(() => props.quotes.filter((item) => (item.changePercent ?? 0) > 0).length)
const fallCount = computed(() => props.quotes.filter((item) => (item.changePercent ?? 0) < 0).length)
const flatCount = computed(
  () => props.quotes.filter((item) => (item.changePercent ?? 0) === 0 || item.changePercent === null).length,
)

const totalVolume = computed(() => {
  return props.quotes.reduce((sum, item) => sum + (item.volume ?? 0), 0)
})

const avgChange = computed(() => {
  if (props.quotes.length === 0) {
    return null
  }
  const total = props.quotes.reduce((sum, item) => sum + (item.changePercent ?? 0), 0)
  return total / props.quotes.length
})
</script>

<template>
  <section class="market-overview">
    <article>
      <span>上涨</span>
      <strong class="up">{{ riseCount }}</strong>
    </article>
    <article>
      <span>下跌</span>
      <strong class="down">{{ fallCount }}</strong>
    </article>
    <article>
      <span>平盘/缺失</span>
      <strong>{{ flatCount }}</strong>
    </article>
    <article>
      <span>总成交量</span>
      <strong>{{ formatVolume(totalVolume) }}</strong>
    </article>
    <article>
      <span>平均涨跌</span>
      <strong :class="(avgChange ?? 0) >= 0 ? 'up' : 'down'">{{ formatPercent(avgChange) }}</strong>
    </article>
  </section>
</template>
