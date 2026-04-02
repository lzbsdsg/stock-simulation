<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useMarketStore } from '@/stores/market'
import { useWatchlistStore } from '@/stores/watchlist'
import { formatPercent, formatPrice, percentClass } from '@/utils/format'

const watchlistStore = useWatchlistStore()
const marketStore = useMarketStore()
const inputCode = ref('')
const draggingCode = ref('')

const watchCodes = computed(() => watchlistStore.items.map((item) => item.stockCode))

onMounted(async () => {
  await refresh()
})

async function refresh(): Promise<void> {
  await watchlistStore.load()
  marketStore.setWatchCodes(watchCodes.value)
}

async function addStock(): Promise<void> {
  const stockCode = inputCode.value.trim().toLowerCase()
  if (!stockCode) {
    return
  }
  try {
    await watchlistStore.add(stockCode)
    inputCode.value = ''
    await refresh()
    ElMessage.success('已添加自选股')
  } catch (error) {
    const message = error instanceof Error ? error.message : '添加失败'
    ElMessage.error(message)
  }
}

async function removeStock(stockCode: string): Promise<void> {
  await watchlistStore.remove(stockCode)
  await refresh()
  ElMessage.success('已移除')
}

function onDragStart(stockCode: string): void {
  draggingCode.value = stockCode
}

async function onDrop(targetCode: string): Promise<void> {
  const from = watchCodes.value.indexOf(draggingCode.value)
  const to = watchCodes.value.indexOf(targetCode)
  if (from < 0 || to < 0 || from === to) {
    return
  }

  const reordered = [...watchCodes.value]
  const [moved] = reordered.splice(from, 1)
  reordered.splice(to, 0, moved)

  await watchlistStore.updateSort(reordered)
  await refresh()
  ElMessage.success('排序已更新')
}
</script>

<template>
  <section class="watchlist-page">
    <header class="watchlist-header">
      <div>
        <h1>自选股</h1>
        <p>最多50只，支持拖拽排序与实时行情刷新。</p>
      </div>
      <el-button type="primary" plain @click="refresh">刷新</el-button>
    </header>

    <div class="watchlist-add-row">
      <el-input v-model="inputCode" placeholder="输入股票代码，如 sh600519" @keyup.enter="addStock" />
      <el-button type="primary" @click="addStock">添加</el-button>
    </div>

    <el-table v-loading="watchlistStore.loading" :data="watchlistStore.items" row-key="stockCode" size="small">
      <el-table-column label="排序" width="70">
        <template #default="scope">{{ scope.$index + 1 }}</template>
      </el-table-column>
      <el-table-column prop="stockCode" label="代码" width="120" />
      <el-table-column prop="stockName" label="名称" min-width="120" />
      <el-table-column label="现价" width="120">
        <template #default="scope">{{ formatPrice(scope.row.currentPrice) }}</template>
      </el-table-column>
      <el-table-column label="涨跌幅" width="120">
        <template #default="scope">
          <span :class="percentClass(scope.row.changePercent)">{{ formatPercent(scope.row.changePercent) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="排序操作" min-width="160">
        <template #default="scope">
          <div
            class="drag-row"
            draggable="true"
            @dragstart="onDragStart(scope.row.stockCode)"
            @dragover.prevent
            @drop="onDrop(scope.row.stockCode)"
          >
            拖到这里交换顺序
          </div>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="scope">
          <el-button link type="danger" @click="removeStock(scope.row.stockCode)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>
