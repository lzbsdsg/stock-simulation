import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { ElNotification } from 'element-plus'
import * as notificationApi from '@/api/notification'
import { useNotificationSocket } from '@/composables/useNotificationSocket'
import { useAuthStore } from '@/stores/auth'
import type { NotificationItem } from '@/types/notification'

export const useNotificationStore = defineStore('notification', () => {
  const notifications = ref<NotificationItem[]>([])
  const loading = ref(false)
  const wsConnected = ref(false)

  const authStore = useAuthStore()

  const unreadCount = computed(() => notifications.value.filter((item) => !item.read).length)

  const socket = useNotificationSocket({
    endpoint: '/ws/market',
    getToken: () => authStore.accessToken || null,
    onNotification: (message) => {
      notifications.value = [message, ...notifications.value.filter((item) => item.notificationId !== message.notificationId)]
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
      const pageResult = await notificationApi.getNotifications(page, size)
      notifications.value = pageResult.records
    } finally {
      loading.value = false
    }
  }

  async function loadUnreadCount(): Promise<number> {
    return notificationApi.getUnreadCount()
  }

  async function markRead(notificationId: number): Promise<void> {
    await notificationApi.markNotificationRead(notificationId)
    notifications.value = notifications.value.map((item) =>
      item.notificationId === notificationId ? { ...item, read: true } : item,
    )
  }

  async function markAllRead(): Promise<void> {
    await notificationApi.markAllNotificationsRead()
    notifications.value = notifications.value.map((item) => ({ ...item, read: true }))
  }

  function connectRealtime(): void {
    if (!authStore.isAuthenticated) {
      return
    }
    socket.connect()
    wsConnected.value = true
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
