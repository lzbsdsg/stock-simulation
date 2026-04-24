import { describe, expect, it } from 'vitest'
import {
  calculateLagMillis,
  nextReconnectDelay,
  RECONNECT_DELAYS_MS,
  resolveWsEndpoint,
  shouldUseNativeTransport,
} from '@/composables/useWebSocket'

describe('useWebSocket helpers', () => {
  it('should cap reconnect delay at max value', () => {
    expect(nextReconnectDelay(0)).toBe(RECONNECT_DELAYS_MS[0])
    expect(nextReconnectDelay(2)).toBe(RECONNECT_DELAYS_MS[2])
    expect(nextReconnectDelay(99)).toBe(RECONNECT_DELAYS_MS[RECONNECT_DELAYS_MS.length - 1])
  })

  it('should calculate lag and avoid negatives', () => {
    expect(calculateLagMillis(1000, 1200)).toBe(200)
    expect(calculateLagMillis(1200, 1000)).toBe(0)
    expect(calculateLagMillis(undefined, 1000)).toBe(0)
  })

  it('should detect native websocket transport endpoints', () => {
    expect(shouldUseNativeTransport('/ws/market-native')).toBe(true)
    expect(shouldUseNativeTransport('wss://example.com/ws/market-native')).toBe(true)
    expect(shouldUseNativeTransport('/ws/market')).toBe(false)
  })

  it('should keep absolute websocket endpoints unchanged', () => {
    expect(resolveWsEndpoint('wss://example.com/ws/market-native')).toBe('wss://example.com/ws/market-native')
    expect(resolveWsEndpoint('ws://example.com/ws/market-native')).toBe('ws://example.com/ws/market-native')
  })
})
