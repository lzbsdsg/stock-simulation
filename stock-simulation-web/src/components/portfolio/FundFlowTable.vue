<script setup lang="ts">
import dayjs from 'dayjs'
import { usePortfolioStore } from '@/stores/portfolio'
import { formatPrice, percentClass } from '@/utils/format'

const portfolioStore = usePortfolioStore()

function flowTypeLabel(type: string): string {
  const map: Record<string, string> = {
    DEPOSIT: '入金',
    WITHDRAW: '出金',
    FREEZE: '冻结',
    UNFREEZE: '解冻',
    TRADE: '交易结算',
    DIVIDEND: '分红',
  }
  return map[type] ?? type
}

function flowTagType(type: string): 'success' | 'warning' | 'info' | 'danger' {
  if (type === 'DEPOSIT' || type === 'DIVIDEND') {
    return 'success'
  }
  if (type === 'FREEZE') {
    return 'warning'
  }
  if (type === 'WITHDRAW') {
    return 'danger'
  }
  return 'info'
}

function formatTime(value: string): string {
  return dayjs(value).format('MM-DD HH:mm:ss')
}
</script>

<template>
  <section class="fund-flow-panel">
    <div class="panel-head">
      <div>
        <h3>资金流水</h3>
        <p class="section-card-subtitle">记录资金冻结、交易结算与余额变化</p>
      </div>
    </div>
    <el-table :data="portfolioStore.fundFlows" size="small">
      <el-table-column label="类型" width="120">
        <template #default="scope">
          <el-tag :type="flowTagType(scope.row.flowType)" size="small" effect="plain">
            {{ flowTypeLabel(scope.row.flowType) }}
          </el-tag>
        </template>
      </el-table-column>
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
