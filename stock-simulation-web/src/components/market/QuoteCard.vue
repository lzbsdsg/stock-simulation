<script setup lang="ts">
import { computed } from 'vue'
import type { Quote } from '@/types/market'
import { formatPrice, formatVolume } from '@/utils/format'

const props = withDefaults(
  defineProps<{
    quote: Quote
    clickable?: boolean
  }>(),
  {
    clickable: true,
  },
)

const emit = defineEmits<{
  (event: 'select', stockCode: string): void
}>()

const changeClass = computed(() => {
  const value = props.quote.changePercent
  if (value === null) {
    return 'flat'
  }
  if (value > 0) {
    return 'up'
  }
  if (value < 0) {
    return 'down'
  }
  return 'flat'
})

function handleClick() {
  if (!props.clickable) {
    return
  }
  emit('select', props.quote.stockCode)
}
</script>

<template>
  <article class="quote-card" :class="{ clickable }" @click="handleClick">
    <header>
      <h3>{{ quote.stockName }}</h3>
      <small>{{ quote.stockCode.toUpperCase() }}</small>
    </header>

    <div class="quote-main">
      <strong class="mono-number">{{ formatPrice(quote.currentPrice) }}</strong>
      <span :class="changeClass">
        {{ quote.changePercent === null ? '--' : `${quote.changePercent.toFixed(2)}%` }}
      </span>
    </div>

    <div class="quote-subline">
      <span>开 {{ formatPrice(quote.openPrice) }}</span>
      <span>昨 {{ formatPrice(quote.closePrice) }}</span>
    </div>

    <footer>
      <span>高 {{ formatPrice(quote.highPrice) }}</span>
      <span>低 {{ formatPrice(quote.lowPrice) }}</span>
      <span>量 {{ formatVolume(quote.volume) }}</span>
      <span>额 {{ formatVolume(quote.amount) }}</span>
    </footer>
  </article>
</template>
