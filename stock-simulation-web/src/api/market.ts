import request, { unwrapResponse } from '@/api/request'
import type {
  KLinePeriod,
  KLinePoint,
  MarketIndexQuote,
  MarketListedPagePayload,
  Quote,
} from '@/types/market'
import type { PageResult } from '@/types/http'

function toNumber(value: unknown): number | null {
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : null
  }
  if (typeof value === 'string' && value.trim() !== '') {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : null
  }
  return null
}

function normalizeQuote(input: Quote): Quote {
  return {
    ...input,
    currentPrice: toNumber(input.currentPrice),
    openPrice: toNumber(input.openPrice),
    closePrice: toNumber(input.closePrice),
    highPrice: toNumber(input.highPrice),
    lowPrice: toNumber(input.lowPrice),
    volume: toNumber(input.volume),
    amount: toNumber(input.amount),
    changePercent: toNumber(input.changePercent),
  }
}

function normalizeKLinePoint(point: KLinePoint): KLinePoint {
  return {
    date: point.date,
    open: toNumber(point.open) ?? 0,
    close: toNumber(point.close) ?? 0,
    high: toNumber(point.high) ?? 0,
    low: toNumber(point.low) ?? 0,
    volume: toNumber(point.volume) ?? 0,
    amount: toNumber(point.amount) ?? 0,
  }
}

export async function getQuote(stockCode: string): Promise<Quote> {
  const data = await unwrapResponse<Quote>(request.get(`/market/quote/${stockCode}`))
  return normalizeQuote(data)
}

export async function batchGetQuotes(stockCodes: string[]): Promise<Quote[]> {
  const data = await unwrapResponse<Quote[]>(
    request.get('/market/quotes', {
      params: { codes: stockCodes },
      paramsSerializer: {
        indexes: null,
      },
    }),
  )

  return data.map(normalizeQuote)
}

export async function getKLine(
  stockCode: string,
  period: KLinePeriod,
  from: string,
  to: string,
): Promise<KLinePoint[]> {
  const data = await unwrapResponse<KLinePoint[]>(
    request.get(`/market/kline/${stockCode}`, {
      params: { period, from, to },
    }),
  )
  return data.map(normalizeKLinePoint)
}

export async function searchStocks(keyword: string): Promise<Quote[]> {
  const data = await unwrapResponse<Quote[]>(
    request.get('/market/search', {
      params: { keyword },
    }),
  )
  return data.map(normalizeQuote)
}

export async function reportVisibleCodes(stockCodes: string[]): Promise<void> {
  await unwrapResponse<void>(request.post('/market/visible-codes', stockCodes))
}

export async function getListedStocksPage(page: number, size: number): Promise<MarketListedPagePayload> {
  const data = await unwrapResponse<PageResult<{ stockCode: string; stockName: string }>>(
    request.get('/market/listed', {
      params: { page, size },
    }),
  )
  return {
    total: data.total,
    page: data.page,
    size: data.size,
    records: data.records.map((item) => ({
      stockCode: item.stockCode.trim().toLowerCase(),
      stockName: item.stockName,
    })),
  }
}

export async function getOfficialIndexQuotes(): Promise<MarketIndexQuote[]> {
  const data = await unwrapResponse<MarketIndexQuote[]>(request.get('/market/indexes'))
  return data.map((item) => ({
    stockCode: item.stockCode.trim().toLowerCase(),
    stockName: item.stockName,
    currentPrice: toNumber(item.currentPrice),
    changeAmount: toNumber(item.changeAmount),
    changePercent: toNumber(item.changePercent),
    volume: toNumber(item.volume),
    amount: toNumber(item.amount),
  }))
}
