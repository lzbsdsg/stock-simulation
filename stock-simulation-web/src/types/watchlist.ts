export interface WatchlistItem {
  stockCode: string
  stockName: string
  currentPrice: number | null
  changePercent: number | null
  sortOrder: number
}
