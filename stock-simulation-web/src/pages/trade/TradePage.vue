<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import OrderForm from '@/components/trade/OrderForm.vue'
import OrderList from '@/components/trade/OrderList.vue'
import TradeHistory from '@/components/trade/TradeHistory.vue'
import { useTradeStore } from '@/stores/trade'

const tradeStore = useTradeStore()
let refreshTimer: number | null = null

onMounted(async () => {
  try {
    await Promise.all([tradeStore.loadOrders(), tradeStore.loadTrades()])
    startAutoRefresh()
  } catch (error) {
    const message = error instanceof Error ? error.message : '交易数据加载失败'
    ElMessage.error(message)
  }
})

onBeforeUnmount(() => {
  stopAutoRefresh()
})

function startAutoRefresh(): void {
  if (refreshTimer !== null) {
    return
  }
  refreshTimer = window.setInterval(() => {
    if (tradeStore.loadingOrders || tradeStore.loadingTrades || tradeStore.placingOrder) {
      return
    }
    void refreshAll().catch(() => undefined)
  }, 3000)
}

function stopAutoRefresh(): void {
  if (refreshTimer === null) {
    return
  }
  window.clearInterval(refreshTimer)
  refreshTimer = null
}

async function refreshAll(): Promise<void> {
  await Promise.all([tradeStore.loadOrders(), tradeStore.loadTrades()])
}
</script>

<template>
  <section class="trade-page">
    <header class="trade-header">
      <div>
        <h1>交易中心</h1>
        <p>支持下单、撤单、委托查询与成交记录查看。</p>
      </div>
      <el-button type="primary" plain @click="refreshAll">刷新</el-button>
    </header>

    <OrderForm @placed="refreshAll" />
    <OrderList />
    <TradeHistory />
  </section>
</template>
