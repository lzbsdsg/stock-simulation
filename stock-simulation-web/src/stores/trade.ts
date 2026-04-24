import { ref } from 'vue'
import { defineStore } from 'pinia'
import * as tradeApi from '@/api/trade'
import type { OrderItem, PlaceOrderPayload, TradeItem } from '@/types/trade'

export const useTradeStore = defineStore('trade', () => {
  const orders = ref<OrderItem[]>([])
  const trades = ref<TradeItem[]>([])
  const totalOrders = ref(0)
  const totalTrades = ref(0)
  const orderScope = ref('today')
  const loadingOrders = ref(false)
  const loadingTrades = ref(false)
  const placingOrder = ref(false)

  async function loadOrders(page = 1, size = 20): Promise<void> {
    loadingOrders.value = true
    try {
      const pageResult = await tradeApi.getOrders(orderScope.value, page, size)
      orders.value = pageResult.records
      totalOrders.value = pageResult.total
    } finally {
      loadingOrders.value = false
    }
  }

  async function loadTrades(page = 1, size = 20): Promise<void> {
    loadingTrades.value = true
    try {
      const pageResult = await tradeApi.getTrades(page, size)
      trades.value = pageResult.records
      totalTrades.value = pageResult.total
    } finally {
      loadingTrades.value = false
    }
  }

  async function refreshTradeData(): Promise<void> {
    await Promise.all([loadOrders(), loadTrades()])
  }

  async function place(payload: PlaceOrderPayload): Promise<OrderItem> {
    placingOrder.value = true
    try {
      const order = await tradeApi.placeOrder(payload)
      await refreshTradeData()
      return order
    } finally {
      placingOrder.value = false
    }
  }

  async function cancel(orderId: number): Promise<void> {
    await tradeApi.cancelOrder(orderId)
    await refreshTradeData()
  }

  function setScope(scope: string): void {
    orderScope.value = scope
  }

  return {
    orders,
    trades,
    totalOrders,
    totalTrades,
    orderScope,
    loadingOrders,
    loadingTrades,
    placingOrder,
    loadOrders,
    loadTrades,
    refreshTradeData,
    place,
    cancel,
    setScope,
  }
})
