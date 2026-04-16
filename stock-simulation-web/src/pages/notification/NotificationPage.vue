<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import dayjs from 'dayjs'
import { ElMessage } from 'element-plus'
import { useNotificationStore } from '@/stores/notification'

const notificationStore = useNotificationStore()
const page = ref(1)
const size = ref(20)

const notifications = computed(() => notificationStore.notifications)

onMounted(async () => {
  await refresh()
})

async function refresh(): Promise<void> {
  try {
    await Promise.all([
      notificationStore.loadNotifications(page.value, size.value),
      notificationStore.loadUnreadCount(),
    ])
  } catch (error) {
    const message = error instanceof Error ? error.message : '通知加载失败'
    ElMessage.error(message)
  }
}

function formatTime(value: string): string {
  return dayjs(value).format('YYYY-MM-DD HH:mm:ss')
}

async function markRead(notificationId: number): Promise<void> {
  try {
    await notificationStore.markRead(notificationId)
    ElMessage.success('已标记为已读')
  } catch (error) {
    const message = error instanceof Error ? error.message : '操作失败'
    ElMessage.error(message)
  }
}

async function markAllRead(): Promise<void> {
  try {
    await notificationStore.markAllRead()
    ElMessage.success('已全部标记为已读')
  } catch (error) {
    const message = error instanceof Error ? error.message : '操作失败'
    ElMessage.error(message)
  }
}
</script>

<template>
  <section class="notification-page">
    <header class="notification-page-header">
      <div>
        <h1>消息通知</h1>
        <p>查看系统通知、成交提醒，并支持标记已读。</p>
      </div>
      <div class="notification-page-actions">
        <el-tag type="danger" effect="dark">未读 {{ notificationStore.unreadCount }}</el-tag>
        <el-button type="primary" plain @click="refresh">刷新</el-button>
        <el-button type="primary" @click="markAllRead">全部已读</el-button>
      </div>
    </header>

    <section class="notification-page-panel">
      <el-empty
        v-if="!notificationStore.loading && notifications.length === 0"
        description="暂无通知"
        :image-size="120"
      />

      <ul v-else v-loading="notificationStore.loading" class="notification-page-list">
        <li
          v-for="item in notifications"
          :key="item.notificationId"
          :class="['notification-page-item', item.read ? 'read' : 'unread']"
        >
          <div class="notification-page-item-head">
            <div class="notification-page-item-meta">
              <strong>{{ item.title }}</strong>
              <el-tag v-if="!item.read" type="danger" size="small">未读</el-tag>
              <el-tag v-else type="info" size="small">已读</el-tag>
            </div>
            <small>{{ formatTime(item.createdAt) }}</small>
          </div>
          <p>{{ item.content }}</p>
          <div class="notification-page-item-actions">
            <el-button
              v-if="!item.read"
              size="small"
              link
              type="primary"
              @click="markRead(item.notificationId)"
            >
              标记已读
            </el-button>
          </div>
        </li>
      </ul>
    </section>
  </section>
</template>
