<script setup lang="ts">
import { computed } from 'vue'
import { useAppStore } from '@/stores/app'

const appStore = useAppStore()

const rateLimitText = computed(() => {
  const { limit, remaining, reset } = appStore.lastRateLimit
  if (limit === undefined && remaining === undefined && reset === undefined) {
    return '暂无请求头信息'
  }

  return `limit=${limit ?? '-'}, remaining=${remaining ?? '-'}, reset=${reset ?? '-'}`
})
</script>

<template>
  <main class="page-shell">
    <section class="card">
      <h1>Stock Simulation Web</h1>
      <p>迭代0前端骨架已就绪：Vue 3 + TypeScript + Vue Router + Pinia + Axios + Element Plus。</p>
      <el-alert type="info" :closable="false" show-icon title="X-RateLimit-* 解析结果">
        <template #default>
          {{ rateLimitText }}
        </template>
      </el-alert>
    </section>
  </main>
</template>
