<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { usePortfolioStore } from '@/stores/portfolio'
import { formatPercent, formatPrice, percentClass } from '@/utils/format'

const portfolioStore = usePortfolioStore()

async function handlePageChange(page: number): Promise<void> {
  try {
    await portfolioStore.loadPositions(page, portfolioStore.positionsSize)
  } catch (error) {
    const message = error instanceof Error ? error.message : '持仓分页加载失败'
    ElMessage.error(message)
  }
}

async function handleSizeChange(size: number): Promise<void> {
  try {
    await portfolioStore.loadPositions(1, size)
  } catch (error) {
    const message = error instanceof Error ? error.message : '持仓分页加载失败'
    ElMessage.error(message)
  }
}
</script>

<template>
  <section class="position-panel">
    <div class="panel-head">
      <div>
        <h3>持仓明细</h3>
        <p class="section-card-subtitle">聚焦仓位规模、成本、市值与盈亏效率</p>
      </div>
    </div>
    <el-table
      v-loading="portfolioStore.loading || portfolioStore.positionsLoading"
      :data="portfolioStore.positions"
      size="small"
    >
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
    <section class="position-pagination">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next"
        :total="portfolioStore.positionsTotal"
        :page-size="portfolioStore.positionsSize"
        :page-sizes="[20, 50, 100, 200]"
        :current-page="portfolioStore.positionsPage"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </section>
  </section>
</template>

<style scoped>
.position-pagination {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
}
</style>
