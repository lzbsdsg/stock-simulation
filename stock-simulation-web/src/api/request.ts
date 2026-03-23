import axios from 'axios'
import type { AxiosResponse } from 'axios'
import type { RateLimitInfo } from '@/types/http'
import { useAppStore } from '@/stores/app'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

function parseRateLimitHeaders(response: AxiosResponse): RateLimitInfo {
  const limit = response.headers['x-ratelimit-limit']
  const remaining = response.headers['x-ratelimit-remaining']
  const reset = response.headers['x-ratelimit-reset']

  return {
    limit: limit !== undefined ? Number(limit) : undefined,
    remaining: remaining !== undefined ? Number(remaining) : undefined,
    reset: reset !== undefined ? Number(reset) : undefined,
  }
}

request.interceptors.response.use(
  (response) => {
    const store = useAppStore()
    store.updateRateLimit(parseRateLimitHeaders(response))
    return response
  },
  (error) => Promise.reject(error),
)

export default request
