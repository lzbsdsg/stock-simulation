<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { useMarketStore } from '@/stores/market'

const router = useRouter()
const authStore = useAuthStore()
const marketStore = useMarketStore()

const displayName = computed(() => authStore.nickname || `用户${authStore.userId ?? ''}`)

onMounted(() => {
  marketStore.connectRealtime()
})

onBeforeUnmount(() => {
  marketStore.disconnectRealtime()
})

async function handleLogout() {
  await authStore.logout()
  marketStore.disconnectRealtime()
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
        <span class="user-name">{{ displayName }}</span>
        <el-button size="small" type="danger" plain @click="handleLogout">退出登录</el-button>
      </div>
    </header>

    <div class="layout-body">
      <aside class="app-sidebar">
        <RouterLink to="/dashboard" class="sidebar-link">仪表盘</RouterLink>
        <RouterLink to="/market" class="sidebar-link">行情中心</RouterLink>
      </aside>

      <main class="layout-content">
        <RouterView />
      </main>
    </div>
  </div>
</template>
