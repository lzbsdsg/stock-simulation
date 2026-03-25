import axios, { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { pinia } from '@/stores'
import { useAppStore } from '@/stores/app'
import type { ApiResponse, CacheStatus, RateLimitInfo } from '@/types/http'
import type { AuthSession, TokenPayload } from '@/types/auth'
import { ApiRequestError } from '@/types/http'
import {
  clearAuthSession,
  getAccessToken,
  getRefreshToken,
  loadAuthSession,
  saveAuthSession,
} from '@/utils/auth-storage'

interface RetriableRequestConfig extends InternalAxiosRequestConfig {
  _retry?: boolean
}

const request = axios.create({
  baseURL: '/api/v1',
  timeout: 10000,
})

let pendingRefresh: Promise<string | null> | null = null

function parseRateLimitHeaders(headers: Record<string, unknown>): RateLimitInfo {
  const limit = parseHeaderNumber(headers['x-ratelimit-limit'])
  const remaining = parseHeaderNumber(headers['x-ratelimit-remaining'])
  const reset = parseHeaderNumber(headers['x-ratelimit-reset'])
  return { limit, remaining, reset }
}

function parseCacheStatus(headers: Record<string, unknown>): CacheStatus {
  const raw = normalizeHeaderValue(headers['x-cache-status'])
  if (raw === 'HIT-L1' || raw === 'HIT-L2' || raw === 'MISS' || raw === 'STALE') {
    return raw
  }
  return 'UNKNOWN'
}

function parseHeaderNumber(value: unknown): number | undefined {
  const normalized = normalizeHeaderValue(value)
  if (!normalized) {
    return undefined
  }
  const parsed = Number(normalized)
  return Number.isFinite(parsed) ? parsed : undefined
}

function normalizeHeaderValue(value: unknown): string | undefined {
  if (typeof value === 'string') {
    return value
  }
  if (Array.isArray(value) && value.length > 0 && typeof value[0] === 'string') {
    return value[0]
  }
  return undefined
}

function updateHttpMeta(headers: Record<string, unknown> | undefined): void {
  if (!headers) {
    return
  }

  const appStore = useAppStore(pinia)
  appStore.updateRateLimit(parseRateLimitHeaders(headers))
  appStore.updateCacheStatus(parseCacheStatus(headers))
}

function isApiResponse(payload: unknown): payload is ApiResponse<unknown> {
  return typeof payload === 'object' && payload !== null && 'code' in payload && 'message' in payload
}

function toApiError(response: AxiosResponse<ApiResponse<unknown>>): ApiRequestError {
  const payload = response.data
  if (isApiResponse(payload)) {
    return new ApiRequestError(payload.message || '请求失败', payload.code, response.status, payload.traceId)
  }
  return new ApiRequestError('请求失败', response.status, response.status)
}

function buildSessionFromTokenPayload(payload: TokenPayload): AuthSession {
  return {
    accessToken: payload.accessToken,
    refreshToken: payload.refreshToken,
    expiresIn: payload.expiresIn,
    userId: payload.userId,
    nickname: payload.nickname,
  }
}

async function refreshTokenWithQueue(): Promise<string | null> {
  if (!pendingRefresh) {
    pendingRefresh = performTokenRefresh().finally(() => {
      pendingRefresh = null
    })
  }
  return pendingRefresh
}

async function performTokenRefresh(): Promise<string | null> {
  const refreshToken = getRefreshToken()
  if (!refreshToken) {
    return null
  }

  try {
    const response = await axios.post<ApiResponse<TokenPayload>>('/api/v1/auth/refresh', {
      refreshToken,
    })

    if (!isApiResponse(response.data) || response.data.code !== 200 || !response.data.data) {
      clearAuthSession()
      return null
    }

    saveAuthSession(buildSessionFromTokenPayload(response.data.data))
    return response.data.data.accessToken
  } catch (_error) {
    clearAuthSession()
    return null
  }
}

function shouldSkipRefresh(url: string): boolean {
  return (
    url.includes('/auth/login') ||
    url.includes('/auth/register') ||
    url.includes('/auth/refresh') ||
    url.includes('/auth/otp/send') ||
    url.includes('/auth/forgot-password') ||
    url.includes('/auth/reset-password')
  )
}

request.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token && !config.headers.Authorization) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    updateHttpMeta(response.headers as Record<string, unknown>)

    if (!isApiResponse(response.data)) {
      return response
    }

    if (response.data.code !== 200) {
      return Promise.reject(toApiError(response as AxiosResponse<ApiResponse<unknown>>))
    }

    return response
  },
  async (error: AxiosError<ApiResponse<unknown>>) => {
    const status = error.response?.status
    const originalRequest = error.config as RetriableRequestConfig | undefined

    updateHttpMeta(error.response?.headers as Record<string, unknown> | undefined)

    if (status === 401 && originalRequest && !originalRequest._retry) {
      const requestUrl = originalRequest.url ?? ''
      if (!shouldSkipRefresh(requestUrl)) {
        originalRequest._retry = true
        const refreshedAccessToken = await refreshTokenWithQueue()
        if (refreshedAccessToken) {
          originalRequest.headers.Authorization = `Bearer ${refreshedAccessToken}`
          return request(originalRequest)
        }
      }
    }

    if (status === 429) {
      ElMessage.warning('请求过于频繁，请稍后重试。')
    }

    if (error.response && isApiResponse(error.response.data)) {
      return Promise.reject(toApiError(error.response as AxiosResponse<ApiResponse<unknown>>))
    }

    const fallbackSession = loadAuthSession()
    const fallbackCode = status ?? 500
    const fallbackMessage = error.message || '网络请求失败'
    const apiError = new ApiRequestError(fallbackMessage, fallbackCode, fallbackCode)

    if (status === 401 && !fallbackSession) {
      clearAuthSession()
    }

    return Promise.reject(apiError)
  },
)

export async function unwrapResponse<T>(
  promise: Promise<AxiosResponse<ApiResponse<T>>>,
): Promise<T> {
  const response = await promise
  const payload = response.data
  if (!isApiResponse(payload)) {
    throw new ApiRequestError('响应格式不正确', 500, response.status)
  }
  return payload.data
}

export default request
