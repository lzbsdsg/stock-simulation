export type TradeSide = 'BUY' | 'SELL'
export type OrderType = 'LIMIT' | 'MARKET'

export interface PlaceOrderPayload {
  clientOrderId: string
  stockCode: string
  side: TradeSide
  orderType: OrderType
  price?: number | null
  quantity: number
}

export interface OrderItem {
  orderId: number
  clientOrderId: string
  stockCode: string
  stockName: string
  side: TradeSide
  orderType: OrderType
  status: string
  price: number
  quantity: number
  filledQuantity: number
  filledAmount: number
  commission: number
  createdAt: string
  updatedAt: string
}

export interface TradeItem {
  tradeId: number
  orderId: number
  stockCode: string
  stockName: string
  side: TradeSide
  tradePrice: number
  tradeQuantity: number
  tradeAmount: number
  commission: number
  tradedAt: string
}
