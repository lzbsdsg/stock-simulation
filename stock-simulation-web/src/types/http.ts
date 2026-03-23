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
