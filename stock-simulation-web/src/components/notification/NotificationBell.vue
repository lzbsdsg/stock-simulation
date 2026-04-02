<script setup lang="ts">
import { computed, onMounted } from 'vue'
import dayjs from 'dayjs'
import { ElMessage } from 'element-plus'
import { useNotificationStore } from '@/stores/notification'

const notificationStore = useNotificationStore()

const recentItems = computed(() => notificationStore.notifications.slice(0, 8))

onMounted(async () => {
  if (notificationStore.notifications.length === 0) {
    await notificationStore.loadNotifications()
  }
})

function formatTime(value: string): string {
  return dayjs(value).format('MM-DD HH:mm:ss')
}

async function handleRead(notificationId: number): Promise<void> {
  await notificationStore.markRead(notificationId)
  ElMessage.success('已标记为已读')
}

async function handleReadAll(): Promise<void> {
  await notificationStore.markAllRead()
  ElMessage.success('已全部标记为已读')
}
</script>

<template>
  <el-popover placement="bottom" :width="360" trigger="click">
    <template #reference>
      <el-badge :value="notificationStore.unreadCount" :hidden="notificationStore.unreadCount === 0">
        <el-button circle plain class="notification-bell">铃</el-button>
      </el-badge>
    </template>

    <div class="notification-panel">
      <header class="notification-header">
        <strong>消息通知</strong>
        <el-button link type="primary" @click="handleReadAll">全部已读</el-button>
      </header>

      <ul v-if="recentItems.length > 0" class="notification-list">
        <li
          v-for="item in recentItems"
          :key="item.notificationId"
          :class="['notification-item', item.read ? 'read' : 'unread']"
        >
          <div class="notification-title-row">
            <strong>{{ item.title }}</strong>
            <small>{{ formatTime(item.createdAt) }}</small>
          </div>
          <p>{{ item.content }}</p>
          <el-button v-if="!item.read" size="small" link type="primary" @click="handleRead(item.notificationId)">
            标记已读
          </el-button>
        </li>
      </ul>

      <el-empty v-else description="暂无通知" :image-size="88" />
    </div>
  </el-popover>
</template>
