<script setup lang="ts">
import dayjs from 'dayjs'
import { useTradeStore } from '@/stores/trade'
import { formatPrice } from '@/utils/format'

const tradeStore = useTradeStore()

function sideLabel(side: string): string {
  return side === 'BUY' ? '买入' : '卖出'
}

function sideTagType(side: string): 'danger' | 'success' {
  return side === 'BUY' ? 'danger' : 'success'
}

function formatTime(value: string): string {
  return dayjs(value).format('MM-DD HH:mm:ss')
}
</script>

<template>
  <section class="trade-history-panel">
    <div class="panel-head">
      <div>
        <h3>成交记录</h3>
        <p class="section-card-subtitle">按成交时间回溯执行结果与成交成本</p>
      </div>
    </div>
    <el-table v-loading="tradeStore.loadingTrades" :data="tradeStore.trades" size="small">
      <el-table-column prop="stockCode" label="代码" width="110" />
      <el-table-column prop="stockName" label="名称" min-width="120" />
      <el-table-column label="方向" width="88">
        <template #default="scope">
          <el-tag :type="sideTagType(scope.row.side)" effect="plain" size="small">{{ sideLabel(scope.row.side) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="成交价" width="110">
        <template #default="scope">{{ formatPrice(scope.row.tradePrice) }}</template>
      </el-table-column>
      <el-table-column prop="tradeQuantity" label="数量" width="90" />
      <el-table-column label="成交额" width="110">
        <template #default="scope">{{ formatPrice(scope.row.tradeAmount) }}</template>
      </el-table-column>
      <el-table-column label="手续费" width="100">
        <template #default="scope">{{ formatPrice(scope.row.commission) }}</template>
      </el-table-column>
      <el-table-column label="成交时间" min-width="140">
        <template #default="scope">{{ formatTime(scope.row.tradedAt) }}</template>
      </el-table-column>
    </el-table>
  </section>
</template>
