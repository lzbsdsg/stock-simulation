<script setup lang="ts">
import { computed } from 'vue'
import { usePortfolioStore } from '@/stores/portfolio'
import { formatPercent, formatPrice, percentClass } from '@/utils/format'

const portfolioStore = usePortfolioStore()

const items = computed(() => {
  const overview = portfolioStore.overview
  if (!overview) {
    return []
  }
  return [
    { label: '总资产', value: formatPrice(overview.totalAssets), cls: '', featured: true },
    { label: '可用资金', value: formatPrice(overview.availableBalance), cls: '', featured: false },
    { label: '冻结资金', value: formatPrice(overview.frozenBalance), cls: '', featured: false },
    { label: '持仓市值', value: formatPrice(overview.marketValue), cls: '', featured: false },
    {
      label: '总收益率',
      value: formatPercent(overview.totalProfitRate),
      cls: percentClass(overview.totalProfitRate),
      featured: false,
    },
    {
      label: '今日盈亏率',
      value: formatPercent(overview.todayProfitRate),
      cls: percentClass(overview.todayProfitRate),
      featured: false,
    },
  ]
})
</script>

<template>
  <section class="portfolio-overview-grid">
    <article
      v-for="item in items"
      :key="item.label"
      class="metric-tile"
      :class="{ 'portfolio-featured-metric': item.featured }"
    >
      <span class="metric-label">{{ item.label }}</span>
      <strong class="metric-value mono-number" :class="item.cls">{{ item.value }}</strong>
    </article>
  </section>
</template>
