<script setup lang="ts">
import { usePortfolioStore } from '@/stores/portfolio'
import { formatPercent, formatPrice, percentClass } from '@/utils/format'

const portfolioStore = usePortfolioStore()
</script>

<template>
  <section class="position-panel">
    <h3>持仓列表</h3>
    <el-table v-loading="portfolioStore.loading" :data="portfolioStore.positions" size="small">
      <el-table-column prop="stockCode" label="代码" width="110" />
      <el-table-column prop="stockName" label="名称" min-width="130" />
      <el-table-column prop="availableQuantity" label="可卖" width="90" />
      <el-table-column prop="frozenQuantity" label="冻结" width="90" />
      <el-table-column label="成本价" width="100">
        <template #default="scope">{{ formatPrice(scope.row.costPrice) }}</template>
      </el-table-column>
      <el-table-column label="现价" width="100">
        <template #default="scope">{{ formatPrice(scope.row.currentPrice) }}</template>
      </el-table-column>
      <el-table-column label="盈亏率" width="110">
        <template #default="scope">
          <span :class="percentClass(scope.row.profitRate)">{{ formatPercent(scope.row.profitRate) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="今日盈亏" width="120">
        <template #default="scope">
          <span :class="percentClass(scope.row.todayProfit)">{{ formatPrice(scope.row.todayProfit) }}</span>
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>
