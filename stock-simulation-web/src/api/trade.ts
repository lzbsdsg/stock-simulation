import request, { unwrapResponse } from '@/api/request'
import type { PageResult } from '@/types/http'
import type { OrderItem, PlaceOrderPayload, TradeItem } from '@/types/trade'

export async function placeOrder(payload: PlaceOrderPayload): Promise<OrderItem> {
  return unwrapResponse<OrderItem>(request.post('/trade/orders', payload))
}

export async function cancelOrder(orderId: number): Promise<void> {
  await unwrapResponse<void>(request.delete(`/trade/orders/${orderId}`))
}

export async function getOrders(scope = 'today', page = 1, size = 20): Promise<PageResult<OrderItem>> {
  return unwrapResponse<PageResult<OrderItem>>(
    request.get('/trade/orders', {
      params: { scope, page, size },
    }),
  )
}

export async function getTrades(page = 1, size = 20): Promise<PageResult<TradeItem>> {
  return unwrapResponse<PageResult<TradeItem>>(
    request.get('/trade/trades', {
      params: { page, size },
    }),
  )
}
