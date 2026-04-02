import { ref } from 'vue'
import { Client, type IFrame, type IMessage } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { nextReconnectDelay, type WsConnectionStatus } from '@/composables/useWebSocket'
import type { NotificationItem } from '@/types/notification'

interface UseNotificationSocketOptions {
  endpoint: string
  getToken: () => string | null
  onNotification?: (message: NotificationItem) => void
  onError?: (message: string) => void
}

export function useNotificationSocket(options: UseNotificationSocketOptions) {
  const status = ref<WsConnectionStatus>('DISCONNECTED')
  const reconnectAttempt = ref(0)

  let reconnectTimer: number | null = null
  let stompClient: Client | null = null
  let manualDisconnected = false

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

    const delay = nextReconnectDelay(reconnectAttempt.value)
    reconnectAttempt.value += 1
    status.value = 'RECONNECTING'

    reconnectTimer = window.setTimeout(() => {
      connect()
    }, delay)
  }

  function handleMessage(frame: IMessage): void {
    if (!frame.body) {
      return
    }
    try {
      const payload = JSON.parse(frame.body) as NotificationItem
      options.onNotification?.(payload)
    } catch (_error) {
      options.onError?.('通知消息解析失败')
    }
  }

  function handleConnect(_frame: IFrame): void {
    reconnectAttempt.value = 0
    status.value = 'CONNECTED'
    stompClient?.subscribe('/user/queue/notification', handleMessage)
  }

  function connect(): void {
    const token = options.getToken()
    if (!token) {
      return
    }

    if (status.value === 'CONNECTED' || status.value === 'CONNECTING') {
      return
    }

    manualDisconnected = false
    status.value = reconnectAttempt.value > 0 ? 'RECONNECTING' : 'CONNECTING'

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
        options.onError?.(frame.headers.message ?? '通知 WebSocket 连接异常')
      },
      onWebSocketClose: () => {
        if (!manualDisconnected) {
          scheduleReconnect()
        } else {
          status.value = 'DISCONNECTED'
        }
      },
      onWebSocketError: () => {
        options.onError?.('通知 WebSocket 网络错误')
      },
    })

    stompClient.activate()
  }

  function disconnect(): void {
    manualDisconnected = true
    clearReconnectTimer()
    if (stompClient) {
      stompClient.deactivate()
      stompClient = null
    }
    status.value = 'DISCONNECTED'
  }

  return {
    status,
    reconnectAttempt,
    connect,
    disconnect,
  }
}
