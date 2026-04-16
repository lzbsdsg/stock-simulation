<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import NotificationBell from '@/components/notification/NotificationBell.vue'
import { useAuthStore } from '@/stores/auth'
import { useMarketStore } from '@/stores/market'
import { useNotificationStore } from '@/stores/notification'
import { useWatchlistStore } from '@/stores/watchlist'

const router = useRouter()
const authStore = useAuthStore()
const marketStore = useMarketStore()
const notificationStore = useNotificationStore()
const watchlistStore = useWatchlistStore()

const displayName = computed(() => authStore.nickname || `用户${authStore.userId ?? ''}`)

onMounted(() => {
  marketStore.connectRealtime()
  notificationStore.connectRealtime()
  notificationStore.loadNotifications().catch(() => undefined)
  watchlistStore
    .load()
    .then(() => {
      const codes = watchlistStore.items.map((item) => item.stockCode)
      if (codes.length > 0) {
        marketStore.setWatchCodes(codes)
      }
    })
    .catch(() => undefined)
})

onBeforeUnmount(() => {
  marketStore.disconnectRealtime()
  notificationStore.disconnectRealtime()
})

async function handleLogout() {
  await authStore.logout()
  marketStore.disconnectRealtime()
  notificationStore.disconnectRealtime()
  ElMessage.success('已安全退出')
  await router.replace('/login')
}
</script>

<template>
  <div class="default-layout">
    <header class="app-header">
      <button class="brand" @click="router.push('/dashboard')">
        <span class="brand-mark">S</span>
        <span class="brand-text">Stock Simulation</span>
      </button>
      <div class="header-actions">
        <NotificationBell />
        <span class="user-name">{{ displayName }}</span>
        <el-button size="small" type="danger" plain @click="handleLogout">退出登录</el-button>
      </div>
    </header>

    <div class="layout-body">
      <aside class="app-sidebar">
        <RouterLink to="/dashboard" class="sidebar-link">仪表盘</RouterLink>
        <RouterLink to="/market" class="sidebar-link">行情中心</RouterLink>
        <RouterLink to="/trade" class="sidebar-link">交易中心</RouterLink>
        <RouterLink to="/portfolio" class="sidebar-link">持仓资产</RouterLink>
        <RouterLink to="/watchlist" class="sidebar-link">自选股</RouterLink>
        <RouterLink to="/notifications" class="sidebar-link">消息通知</RouterLink>
      </aside>

      <main class="layout-content">
        <RouterView />
      </main>
    </div>
  </div>
</template>
