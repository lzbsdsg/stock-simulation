import request, { unwrapResponse } from '@/api/request'
import type { PageResult } from '@/types/http'
import type {
  EquityCurve,
  FundFlowItem,
  PortfolioOverview,
  PositionItem,
} from '@/types/portfolio'

export async function getOverview(): Promise<PortfolioOverview> {
  return unwrapResponse<PortfolioOverview>(request.get('/portfolio/overview'))
}

export async function getPositions(): Promise<PositionItem[]> {
  return unwrapResponse<PositionItem[]>(request.get('/portfolio/positions'))
}

export async function getFundFlows(page = 1, size = 20): Promise<PageResult<FundFlowItem>> {
  return unwrapResponse<PageResult<FundFlowItem>>(
    request.get('/portfolio/fund-flows', {
      params: { page, size },
    }),
  )
}

export async function getEquityCurve(days = 30): Promise<EquityCurve> {
  return unwrapResponse<EquityCurve>(
    request.get('/portfolio/equity-curve', {
      params: { days },
    }),
  )
}
