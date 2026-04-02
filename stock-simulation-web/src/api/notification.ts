import request, { unwrapResponse } from '@/api/request'
import type { PageResult } from '@/types/http'
import type { NotificationItem } from '@/types/notification'

export async function getNotifications(page = 1, size = 20): Promise<PageResult<NotificationItem>> {
  return unwrapResponse<PageResult<NotificationItem>>(
    request.get('/notifications', {
      params: { page, size },
    }),
  )
}

export async function getUnreadCount(): Promise<number> {
  return unwrapResponse<number>(request.get('/notifications/unread-count'))
}

export async function markNotificationRead(notificationId: number): Promise<void> {
  await unwrapResponse<void>(request.put(`/notifications/${notificationId}/read`))
}

export async function markAllNotificationsRead(): Promise<void> {
  await unwrapResponse<void>(request.put('/notifications/read-all'))
}
