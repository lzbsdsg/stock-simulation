<script setup lang="ts">
import { computed } from 'vue'
import type { Quote } from '@/types/market'

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
      <strong>{{ quote.currentPrice === null ? '--' : quote.currentPrice.toFixed(2) }}</strong>
      <span :class="changeClass">
        {{ quote.changePercent === null ? '--' : `${quote.changePercent.toFixed(2)}%` }}
      </span>
    </div>

    <footer>
      <span>高 {{ quote.highPrice === null ? '--' : quote.highPrice.toFixed(2) }}</span>
      <span>低 {{ quote.lowPrice === null ? '--' : quote.lowPrice.toFixed(2) }}</span>
      <span>量 {{ quote.volume === null ? '--' : quote.volume }}</span>
    </footer>
  </article>
</template>
