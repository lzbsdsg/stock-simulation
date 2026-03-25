import { describe, expect, it } from 'vitest'
import { calculateLagMillis, nextReconnectDelay, RECONNECT_DELAYS_MS } from '@/composables/useWebSocket'

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
})
