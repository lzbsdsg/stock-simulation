import { ref } from 'vue'
import { Client, type IFrame, type IMessage, type StompSubscription } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import type { Quote, WsQuotePayload } from '@/types/market'

export type WsConnectionStatus = 'DISCONNECTED' | 'CONNECTING' | 'CONNECTED' | 'RECONNECTING'

export const RECONNECT_DELAYS_MS = [1000, 2000, 4000, 8000, 16000, 30000] as const

export function nextReconnectDelay(attempt: number): number {
  if (attempt <= 0) {
    return RECONNECT_DELAYS_MS[0]
  }

  if (attempt >= RECONNECT_DELAYS_MS.length) {
    return RECONNECT_DELAYS_MS[RECONNECT_DELAYS_MS.length - 1]
  }

  return RECONNECT_DELAYS_MS[attempt]
}

export function calculateLagMillis(pushTsMillis?: number, nowMs = Date.now()): number {
  if (!pushTsMillis || !Number.isFinite(pushTsMillis)) {
    return 0
  }
  return Math.max(0, nowMs - pushTsMillis)
}

interface UseWebSocketOptions {
  endpoint: string
  getToken: () => string | null
  degradeThresholdMs?: number
  degradedRenderIntervalMs?: number
  onStatusChange?: (status: WsConnectionStatus) => void
  onQuote?: (quote: Quote) => void
  onError?: (message: string) => void
}

function normalizeQuote(payload: WsQuotePayload): Quote | null {
  if (!payload.stockCode) {
    return null
  }

  const normalizedCode = payload.stockCode.trim().toLowerCase()
  const normalizedName = typeof payload.stockName === 'string' ? payload.stockName.trim() : ''

  return {
    stockCode: normalizedCode,
    stockName: normalizedName || normalizedCode,
    currentPrice: toNumber(payload.currentPrice),
    openPrice: toNumber(payload.openPrice),
    closePrice: toNumber(payload.closePrice),
    highPrice: toNumber(payload.highPrice),
    lowPrice: toNumber(payload.lowPrice),
    volume: toNumber(payload.volume),
    amount: toNumber(payload.amount),
    changePercent: toNumber(payload.changePercent),
    timestamp: payload.timestamp ?? new Date().toISOString(),
  }
}

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

export function useWebSocket(options: UseWebSocketOptions) {
  const status = ref<WsConnectionStatus>('DISCONNECTED')
  const reconnectAttempt = ref(0)
  const lastLagMs = ref(0)
  const isDegraded = ref(false)

  const degradeThresholdMs = options.degradeThresholdMs ?? 5000
  const degradedRenderIntervalMs = options.degradedRenderIntervalMs ?? 5000
  const activeTopics = new Set<string>()
  const subscriptions = new Map<string, StompSubscription>()
  let lastDeliveredAtMs = 0

  let reconnectTimer: number | null = null
  let stompClient: Client | null = null
  let manualDisconnected = false

  function notifyStatus(next: WsConnectionStatus): void {
    status.value = next
    options.onStatusChange?.(next)
  }

  function clearReconnectTimer(): void {
    if (reconnectTimer === null) {
      return
    }
    window.clearTimeout(reconnectTimer)
    reconnectTimer = null
  }

  function scheduleReconnect(): void {
    if (manualDisconnected) {
      return
    }

    clearReconnectTimer()
    const delayMs = nextReconnectDelay(reconnectAttempt.value)
    reconnectAttempt.value += 1
    notifyStatus('RECONNECTING')

    reconnectTimer = window.setTimeout(() => {
      connect()
    }, delayMs)
  }

  function handleConnect(_frame: IFrame): void {
    reconnectAttempt.value = 0
    notifyStatus('CONNECTED')
    for (const topic of activeTopics) {
      subscribeTopic(topic)
    }
  }

  function handleMessage(message: IMessage): void {
    if (!message.body) {
      return
    }

    try {
      const payload = JSON.parse(message.body) as WsQuotePayload
      const lag = calculateLagMillis(payload.wsPushTsMillis)
      lastLagMs.value = lag
      isDegraded.value = lag > degradeThresholdMs

      const now = Date.now()
      if (isDegraded.value && now - lastDeliveredAtMs < degradedRenderIntervalMs) {
        return
      }

      const quote = normalizeQuote(payload)
      if (quote) {
        lastDeliveredAtMs = now
        options.onQuote?.(quote)
      }
    } catch (_error) {
      options.onError?.('行情推送解析失败')
    }
  }

  function subscribeTopic(topic: string): void {
    if (!stompClient || !stompClient.connected) {
      return
    }

    if (subscriptions.has(topic)) {
      return
    }

    const subscription = stompClient.subscribe(topic, handleMessage)
    subscriptions.set(topic, subscription)
  }

  function unsubscribeTopic(topic: string): void {
    const subscription = subscriptions.get(topic)
    if (!subscription) {
      return
    }

    subscription.unsubscribe()
    subscriptions.delete(topic)
  }

  function connect(): void {
    const token = options.getToken()
    if (!token) {
      options.onError?.('登录已过期，请重新登录。')
      return
    }

    if (status.value === 'CONNECTED' || status.value === 'CONNECTING') {
      return
    }

    manualDisconnected = false
    notifyStatus(reconnectAttempt.value > 0 ? 'RECONNECTING' : 'CONNECTING')

    const encodedToken = encodeURIComponent(token)
    const sockJsUrl = `${options.endpoint}?access_token=${encodedToken}`

    stompClient = new Client({
      webSocketFactory: () => new SockJS(sockJsUrl),
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      reconnectDelay: 0,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      debug: () => undefined,
      onConnect: handleConnect,
      onStompError: (frame) => {
        options.onError?.(frame.headers.message ?? 'WebSocket 连接异常')
      },
      onWebSocketClose: () => {
        subscriptions.clear()
        if (!manualDisconnected) {
          scheduleReconnect()
        } else {
          notifyStatus('DISCONNECTED')
        }
      },
      onWebSocketError: () => {
        options.onError?.('WebSocket 网络错误')
      },
    })

    stompClient.activate()
  }

  function disconnect(): void {
    manualDisconnected = true
    clearReconnectTimer()

    for (const [topic, subscription] of subscriptions.entries()) {
      subscription.unsubscribe()
      subscriptions.delete(topic)
    }

    if (stompClient) {
      stompClient.deactivate()
      stompClient = null
    }

    notifyStatus('DISCONNECTED')
  }

  function normalizeCode(stockCode: string): string {
    return stockCode.trim().toLowerCase()
  }

  function subscribeQuote(stockCode: string): void {
    const code = normalizeCode(stockCode)
    if (!code) {
      return
    }

    const topic = `/topic/market/quote/${code}`
    activeTopics.add(topic)
    subscribeTopic(topic)
  }

  function unsubscribeQuote(stockCode: string): void {
    const code = normalizeCode(stockCode)
    if (!code) {
      return
    }

    const topic = `/topic/market/quote/${code}`
    activeTopics.delete(topic)
    unsubscribeTopic(topic)
  }

  return {
    status,
    reconnectAttempt,
    lastLagMs,
    isDegraded,
    connect,
    disconnect,
    subscribeQuote,
    unsubscribeQuote,
  }
}
