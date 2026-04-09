import { ref } from 'vue'
import { defineStore } from 'pinia'
import { ElNotification } from 'element-plus'
import * as notificationApi from '@/api/notification'
import { useNotificationSocket } from '@/composables/useNotificationSocket'
import { useAuthStore } from '@/stores/auth'
import type { NotificationItem } from '@/types/notification'

export const useNotificationStore = defineStore('notification', () => {
  const notifications = ref<NotificationItem[]>([])
  const unreadCount = ref(0)
  const loading = ref(false)
  const wsConnected = ref(false)

  const authStore = useAuthStore()

  const socket = useNotificationSocket({
    endpoint: '/ws/market',
    getToken: () => authStore.accessToken || null,
    onNotification: (message) => {
      const existing = notifications.value.find((item) => item.notificationId === message.notificationId)
      notifications.value = [message, ...notifications.value.filter((item) => item.notificationId !== message.notificationId)]
      if (!message.read && (!existing || existing.read)) {
        unreadCount.value += 1
      }
      ElNotification({
        title: message.title,
        message: message.content,
        type: 'success',
        duration: 3500,
      })
    },
  })

  async function loadNotifications(page = 1, size = 20): Promise<void> {
    loading.value = true
    try {
      const [pageResult, unread] = await Promise.all([
        notificationApi.getNotifications(page, size),
        notificationApi.getUnreadCount(),
      ])
      notifications.value = pageResult.records
      unreadCount.value = Math.max(0, unread)
    } finally {
      loading.value = false
    }
  }

  async function loadUnreadCount(): Promise<number> {
    const unread = await notificationApi.getUnreadCount()
    unreadCount.value = Math.max(0, unread)
    return unreadCount.value
  }

  async function markRead(notificationId: number): Promise<void> {
    const target = notifications.value.find((item) => item.notificationId === notificationId)
    await notificationApi.markNotificationRead(notificationId)
    notifications.value = notifications.value.map((item) =>
      item.notificationId === notificationId ? { ...item, read: true } : item,
    )
    if (target && !target.read) {
      unreadCount.value = Math.max(0, unreadCount.value - 1)
    }
  }

  async function markAllRead(): Promise<void> {
    await notificationApi.markAllNotificationsRead()
    notifications.value = notifications.value.map((item) => ({ ...item, read: true }))
    unreadCount.value = 0
  }

  function connectRealtime(): void {
    if (!authStore.isAuthenticated) {
      return
    }
    socket.connect()
    wsConnected.value = true
    void loadUnreadCount().catch(() => undefined)
  }

  function disconnectRealtime(): void {
    socket.disconnect()
    wsConnected.value = false
  }

  return {
    notifications,
    loading,
    wsConnected,
    socketStatus: socket.status,
    unreadCount,
    loadNotifications,
    loadUnreadCount,
    markRead,
    markAllRead,
    connectRealtime,
    disconnectRealtime,
  }
})
