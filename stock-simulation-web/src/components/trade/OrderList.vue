<script setup lang="ts">
import dayjs from 'dayjs'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useTradeStore } from '@/stores/trade'
import { formatPrice } from '@/utils/format'

const tradeStore = useTradeStore()

function canCancel(status: string): boolean {
  return status === 'PENDING' || status === 'PARTIAL_FILLED'
}

function sideLabel(side: string): string {
  return side === 'BUY' ? '买入' : '卖出'
}

function sideTagType(side: string): 'danger' | 'success' {
  return side === 'BUY' ? 'danger' : 'success'
}

function statusLabel(status: string): string {
  const map: Record<string, string> = {
    PENDING: '待成交',
    PARTIAL_FILLED: '部分成交',
    FILLED: '已成交',
    CANCELLED: '已撤单',
    REJECTED: '已拒绝',
  }
  return map[status] ?? status
}

function statusTagType(status: string): 'info' | 'warning' | 'success' | 'danger' {
  if (status === 'PENDING') {
    return 'warning'
  }
  if (status === 'PARTIAL_FILLED') {
    return 'info'
  }
  if (status === 'FILLED') {
    return 'success'
  }
  return 'danger'
}

function fillRatio(quantity: number, filledQuantity: number): string {
  if (!quantity) {
    return '0.00%'
  }
  return `${((filledQuantity / quantity) * 100).toFixed(2)}%`
}

function formatTime(value: string): string {
  return dayjs(value).format('MM-DD HH:mm:ss')
}

async function cancelOrder(orderId: number): Promise<void> {
  try {
    await ElMessageBox.confirm('确认撤销该委托单吗？', '撤单确认', { type: 'warning' })
    await tradeStore.cancel(orderId)
    ElMessage.success('撤单成功')
  } catch (_error) {
    // 用户主动取消弹窗时忽略
  }
}
</script>

<template>
  <section class="order-list-panel">
    <div class="panel-head">
      <div>
        <h3>委托列表</h3>
        <p class="section-card-subtitle">状态、成交进度与撤单动作集中展示</p>
      </div>
      <el-segmented
        :model-value="tradeStore.orderScope"
        :options="[
          { label: '当日', value: 'today' },
          { label: '历史', value: 'history' },
          { label: '全部', value: 'all' },
        ]"
        @change="(value: string | number | boolean) => { tradeStore.setScope(String(value)); tradeStore.loadOrders(); }"
      />
    </div>

    <el-table v-loading="tradeStore.loadingOrders" :data="tradeStore.orders" size="small">
      <el-table-column prop="stockCode" label="代码" width="110" />
      <el-table-column prop="stockName" label="名称" min-width="120" />
      <el-table-column label="方向" width="88">
        <template #default="scope">
          <el-tag :type="sideTagType(scope.row.side)" effect="plain" size="small">{{ sideLabel(scope.row.side) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="价格" width="100">
        <template #default="scope">{{ formatPrice(scope.row.price) }}</template>
      </el-table-column>
      <el-table-column prop="quantity" label="数量" width="90" />
      <el-table-column prop="filledQuantity" label="已成" width="90" />
      <el-table-column label="成交进度" width="120">
        <template #default="scope">{{ fillRatio(scope.row.quantity, scope.row.filledQuantity) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="scope">
          <el-tag size="small" :type="statusTagType(scope.row.status)" effect="plain">
            {{ statusLabel(scope.row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="时间" min-width="140">
        <template #default="scope">{{ formatTime(scope.row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="scope">
          <el-button
            v-if="canCancel(scope.row.status)"
            link
            type="danger"
            @click="cancelOrder(scope.row.orderId)"
          >
            撤单
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>
