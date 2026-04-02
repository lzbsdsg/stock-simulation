<script setup lang="ts">
import dayjs from 'dayjs'
import { usePortfolioStore } from '@/stores/portfolio'
import { formatPrice, percentClass } from '@/utils/format'

const portfolioStore = usePortfolioStore()

function formatTime(value: string): string {
  return dayjs(value).format('MM-DD HH:mm:ss')
}
</script>

<template>
  <section class="fund-flow-panel">
    <h3>资金流水</h3>
    <el-table :data="portfolioStore.fundFlows" size="small">
      <el-table-column prop="flowType" label="类型" width="120" />
      <el-table-column label="金额" width="120">
        <template #default="scope">
          <span :class="percentClass(scope.row.amount)">{{ formatPrice(scope.row.amount) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="余额" width="120">
        <template #default="scope">{{ formatPrice(scope.row.balanceAfter) }}</template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="160" />
      <el-table-column label="时间" min-width="140">
        <template #default="scope">{{ formatTime(scope.row.createdAt) }}</template>
      </el-table-column>
    </el-table>
  </section>
</template>
