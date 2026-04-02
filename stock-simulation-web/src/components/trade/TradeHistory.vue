<script setup lang="ts">
import dayjs from 'dayjs'
import { useTradeStore } from '@/stores/trade'
import { formatPrice } from '@/utils/format'

const tradeStore = useTradeStore()

function formatTime(value: string): string {
  return dayjs(value).format('MM-DD HH:mm:ss')
}
</script>

<template>
  <section class="trade-history-panel">
    <h3>成交记录</h3>
    <el-table v-loading="tradeStore.loadingTrades" :data="tradeStore.trades" size="small">
      <el-table-column prop="stockCode" label="代码" width="110" />
      <el-table-column prop="side" label="方向" width="80" />
      <el-table-column label="成交价" width="110">
        <template #default="scope">{{ formatPrice(scope.row.tradePrice) }}</template>
      </el-table-column>
      <el-table-column prop="tradeQuantity" label="数量" width="90" />
      <el-table-column label="成交额" width="110">
        <template #default="scope">{{ formatPrice(scope.row.tradeAmount) }}</template>
      </el-table-column>
      <el-table-column label="成交时间" min-width="140">
        <template #default="scope">{{ formatTime(scope.row.tradedAt) }}</template>
      </el-table-column>
    </el-table>
  </section>
</template>
