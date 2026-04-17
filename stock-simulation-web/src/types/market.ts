export type KLinePeriod = 'DAILY' | 'WEEKLY' | 'MONTHLY'

export interface Quote {
  stockCode: string
  stockName: string
  currentPrice: number | null
  openPrice: number | null
  closePrice: number | null
  highPrice: number | null
  lowPrice: number | null
  volume: number | null
  amount: number | null
  changePercent: number | null
  timestamp: string
}

export interface KLinePoint {
  date: string
  open: number
  close: number
  high: number
  low: number
  volume: number
  amount: number
}

export interface QuoteSuggestion {
  stockCode: string
  stockName: string
}

export interface WsQuotePayload extends Partial<Quote> {
  wsPushTsMillis?: number
}

export interface MarketListedItem {
  stockCode: string
  stockName: string
}

export interface MarketListedPagePayload {
  total: number
  page: number
  size: number
  records: MarketListedItem[]
}

export interface MarketIndexQuote {
  stockCode: string
  stockName: string
  currentPrice: number | null
  changeAmount: number | null
  changePercent: number | null
  volume: number | null
  amount: number | null
}
