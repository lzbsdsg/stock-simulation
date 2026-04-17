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
    <header class="page-head">
      <div>
        <h1 class="page-title">交易执行台</h1>
        <p class="page-subtitle">下单动作与委托成交记录分区展示，降低误操作风险。</p>
      </div>
      <div class="trade-head-actions">
        <el-button type="primary" plain @click="refreshAll">刷新</el-button>
      </div>
    </header>

    <section class="trade-main-grid">
      <div class="trade-left-column">
        <OrderForm @placed="refreshAll" />

        <section class="section-card trade-tips-panel">
          <div class="section-card-head">
            <div>
              <h2 class="section-card-title">交易提醒</h2>
              <p class="section-card-subtitle">下单前建议复核价格、数量与可用资金</p>
            </div>
          </div>

          <ul class="trade-tips-list">
            <li>限价单在价格偏离明显时可能无法成交。</li>
            <li>委托数量需满足 100 股整数倍规则。</li>
            <li>频繁撤单会影响委托队列稳定性，建议审慎操作。</li>
          </ul>
        </section>
      </div>

      <div class="trade-right-column">
        <OrderList />
        <TradeHistory />
      </div>
    </section>
  </section>
</template>

<style scoped>
.trade-main-grid {
  display: grid;
  gap: 14px;
  grid-template-columns: minmax(330px, 0.95fr) minmax(0, 1.45fr);
  align-items: start;
}

.trade-left-column,
.trade-right-column {
  display: grid;
  gap: 14px;
}

.trade-tips-list {
  margin: 0;
  padding-left: 18px;
  color: var(--text-secondary);
  line-height: 1.7;
  font-size: 13px;
}

@media (max-width: 1120px) {
  .trade-main-grid {
    grid-template-columns: 1fr;
  }
}
</style>
