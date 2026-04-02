export type CacheStatus = 'HIT-L1' | 'HIT-L2' | 'MISS' | 'STALE' | 'UNKNOWN'

export interface RateLimitInfo {
  limit?: number
  remaining?: number
  reset?: number
}

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  traceId?: string
  timestamp?: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export class ApiRequestError extends Error {
  code: number
  httpStatus: number
  traceId?: string

  constructor(message: string, code: number, httpStatus: number, traceId?: string) {
    super(message)
    this.name = 'ApiRequestError'
    this.code = code
    this.httpStatus = httpStatus
    this.traceId = traceId
  }
}
