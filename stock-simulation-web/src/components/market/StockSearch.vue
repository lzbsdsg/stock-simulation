<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useMarketStore } from '@/stores/market'

interface SuggestionItem {
  value: string
  stockCode: string
}

const marketStore = useMarketStore()
const emit = defineEmits<{
  (event: 'select', stockCode: string): void
}>()

const keyword = ref('')
let debounceTimer: number | null = null

async function querySearch(queryString: string, callback: (items: SuggestionItem[]) => void) {
  if (debounceTimer !== null) {
    window.clearTimeout(debounceTimer)
  }

  if (!queryString.trim()) {
    callback([])
    return
  }

  debounceTimer = window.setTimeout(async () => {
    try {
      await marketStore.search(queryString)
      callback(
        marketStore.searchResults.map((item) => ({
          value: `${item.stockCode.toUpperCase()} ${item.stockName}`,
          stockCode: item.stockCode,
        })),
      )
    } catch (error) {
      const message = error instanceof Error ? error.message : '搜索失败'
      ElMessage.error(message)
      callback([])
    }
  }, 250)
}

function handleSelect(item: SuggestionItem): void {
  emit('select', item.stockCode)
  keyword.value = item.value
}

function submitSearch(): void {
  if (!keyword.value.trim()) {
    return
  }

  const code = keyword.value.trim().split(' ')[0].toLowerCase()
  if (!code) {
    return
  }

  emit('select', code)
}
</script>

<template>
  <div class="stock-search">
    <el-autocomplete
      v-model="keyword"
      :fetch-suggestions="querySearch"
      placeholder="输入股票代码或名称，例如 sh600519 / 贵州茅台"
      clearable
      @select="handleSelect"
      @keyup.enter="submitSearch"
    />
    <el-button type="primary" @click="submitSearch">查看详情</el-button>
  </div>
</template>
