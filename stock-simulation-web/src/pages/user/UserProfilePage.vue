<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as userApi from '@/api/user'
import { useAuthStore } from '@/stores/auth'
import type { UserProfile } from '@/types/user'

const authStore = useAuthStore()

const loading = ref(false)
const saving = ref(false)
const profile = ref<UserProfile | null>(null)

const form = reactive({
  nickname: '',
})

const statusTagType = computed(() => {
  const status = profile.value?.status
  if (status === 'ACTIVE') {
    return 'success'
  }
  if (status === 'LOCKED') {
    return 'warning'
  }
  if (status === 'DISABLED') {
    return 'danger'
  }
  return 'info'
})

const statusLabel = computed(() => {
  const status = profile.value?.status
  if (status === 'ACTIVE') {
    return '正常'
  }
  if (status === 'LOCKED') {
    return '锁定'
  }
  if (status === 'DISABLED') {
    return '禁用'
  }
  return '未知'
})

onMounted(() => {
  loadProfile().catch(() => undefined)
})

function applyProfile(next: UserProfile): void {
  profile.value = next
  form.nickname = next.nickname
  authStore.applyProfileSnapshot(next.nickname)
}

async function loadProfile(): Promise<void> {
  loading.value = true
  try {
    const current = await userApi.getCurrentUserProfile()
    applyProfile(current)
  } catch (error) {
    const message = error instanceof Error ? error.message : '用户资料加载失败'
    ElMessage.error(message)
  } finally {
    loading.value = false
  }
}

async function saveProfile(): Promise<void> {
  const nickname = form.nickname.trim()
  if (!nickname) {
    ElMessage.warning('昵称不能为空')
    return
  }

  saving.value = true
  try {
    const updated = await userApi.updateCurrentUserProfile({
      nickname,
    })
    applyProfile(updated)
    ElMessage.success('用户资料已更新')
  } catch (error) {
    const message = error instanceof Error ? error.message : '资料更新失败'
    ElMessage.error(message)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <section class="profile-page">
    <header class="profile-header">
      <div>
        <h1>个人资料</h1>
        <p>管理昵称和账号基础信息。</p>
      </div>
      <el-button plain :loading="loading" @click="loadProfile">刷新</el-button>
    </header>

    <section class="kpi-strip">
      <span class="kpi-pill pill-brand">
        账号角色
        <strong>{{ profile?.role || '--' }}</strong>
      </span>
      <span class="kpi-pill" :class="statusTagType === 'success' ? 'pill-safe' : 'pill-risk'">
        当前状态
        <strong>{{ statusLabel }}</strong>
      </span>
      <span class="kpi-pill pill-brand">
        用户 ID
        <strong class="mono-number">{{ profile?.userId ?? '--' }}</strong>
      </span>
    </section>

    <section v-loading="loading" class="profile-panel">
      <el-form class="profile-form" label-position="top" @submit.prevent="saveProfile">
        <div class="profile-form-grid">
          <el-form-item label="用户 ID">
            <el-input :model-value="String(profile?.userId ?? '-')" disabled />
          </el-form-item>

          <el-form-item label="邮箱">
            <el-input :model-value="profile?.email ?? '-'" disabled />
          </el-form-item>

          <el-form-item label="账号角色">
            <el-input :model-value="profile?.role ?? '-'" disabled />
          </el-form-item>

          <el-form-item label="账号状态">
            <el-tag :type="statusTagType">{{ profile?.status ?? 'UNKNOWN' }}</el-tag>
          </el-form-item>

          <el-form-item label="昵称">
            <el-input v-model="form.nickname" maxlength="50" placeholder="请输入昵称" />
          </el-form-item>
        </div>

        <div class="profile-form-actions">
          <el-button @click="loadProfile">重置</el-button>
          <el-button type="primary" :loading="saving" @click="saveProfile">保存更改</el-button>
        </div>
      </el-form>
    </section>
  </section>
</template>