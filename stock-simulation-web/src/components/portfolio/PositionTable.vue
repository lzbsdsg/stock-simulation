<script setup lang="ts">
import { usePortfolioStore } from '@/stores/portfolio'
import { formatPercent, formatPrice, percentClass } from '@/utils/format'

const portfolioStore = usePortfolioStore()
</script>

<template>
  <section class="position-panel">
    <div class="panel-head">
      <div>
        <h3>持仓明细</h3>
        <p class="section-card-subtitle">聚焦仓位规模、成本、市值与盈亏效率</p>
      </div>
    </div>
    <el-table v-loading="portfolioStore.loading" :data="portfolioStore.positions" size="small">
      <el-table-column prop="stockCode" label="代码" width="110" />
      <el-table-column prop="stockName" label="名称" min-width="130" />
      <el-table-column prop="totalQuantity" label="持仓" width="90" />
      <el-table-column prop="availableQuantity" label="可卖" width="90" />
      <el-table-column prop="frozenQuantity" label="冻结" width="90" />
      <el-table-column label="成本价" width="100">
        <template #default="scope">{{ formatPrice(scope.row.costPrice) }}</template>
      </el-table-column>
      <el-table-column label="现价" width="100">
        <template #default="scope">{{ formatPrice(scope.row.currentPrice) }}</template>
      </el-table-column>
      <el-table-column label="市值" width="120">
        <template #default="scope">{{ formatPrice(scope.row.marketValue) }}</template>
      </el-table-column>
      <el-table-column label="浮动盈亏" width="120">
        <template #default="scope">
          <span :class="percentClass(scope.row.profit)">{{ formatPrice(scope.row.profit) }}</span>
        </template>
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
