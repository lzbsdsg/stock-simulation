<script setup lang="ts">
import dayjs from 'dayjs'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useTradeStore } from '@/stores/trade'
import { formatPrice } from '@/utils/format'

const tradeStore = useTradeStore()

function canCancel(status: string): boolean {
  return status === 'PENDING' || status === 'PARTIAL_FILLED'
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
      <h3>委托列表</h3>
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
      <el-table-column prop="side" label="方向" width="80" />
      <el-table-column label="价格" width="100">
        <template #default="scope">{{ formatPrice(scope.row.price) }}</template>
      </el-table-column>
      <el-table-column prop="quantity" label="数量" width="90" />
      <el-table-column prop="filledQuantity" label="已成" width="90" />
      <el-table-column prop="status" label="状态" width="130" />
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
