export interface PortfolioOverview {
  totalAssets: number
  availableBalance: number
  frozenBalance: number
  marketValue: number
  initialBalance: number
  totalProfit: number
  totalProfitRate: number
  todayProfit: number
  todayProfitRate: number
}

export interface PositionItem {
  positionId: number
  stockCode: string
  stockName: string
  totalQuantity: number
  availableQuantity: number
  frozenQuantity: number
  costPrice: number
  currentPrice: number
  marketValue: number
  profit: number
  profitRate: number
  todayProfit: number
  frozenUntil?: string | null
}

export interface FundFlowItem {
  flowId: number
  flowType: string
  amount: number
  balanceAfter: number
  orderId?: number | null
  remark?: string | null
  createdAt: string
}

export interface EquityCurvePoint {
  date: string
  totalAssets: number
  profitRate: number
}

export interface EquityCurve {
  points: EquityCurvePoint[]
  maxDrawdown: number
  maxDrawdownDate?: string | null
}
