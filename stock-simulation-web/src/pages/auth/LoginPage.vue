<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AuthLayout from '@/layouts/AuthLayout.vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const form = reactive({
  email: '',
  password: '',
})

const loading = ref(false)

async function submit() {
  loading.value = true
  try {
    await authStore.login({ email: form.email, password: form.password })
    ElMessage.success('登录成功')

    const redirectPath = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
    await router.replace(redirectPath)
  } catch (error) {
    const message = error instanceof Error ? error.message : '登录失败，请稍后重试'
    ElMessage.error(message)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AuthLayout>
    <header class="auth-header">
      <h1>欢迎回到交易台</h1>
      <p>登录后即可查看实时行情与 K 线数据。</p>
    </header>

    <el-form class="auth-form" :model="form" label-position="top" @submit.prevent="submit">
      <el-form-item label="邮箱">
        <el-input v-model="form.email" type="email" autocomplete="email" placeholder="you@example.com" />
      </el-form-item>

      <el-form-item label="密码">
        <el-input
          v-model="form.password"
          type="password"
          show-password
          autocomplete="current-password"
          placeholder="请输入密码"
        />
      </el-form-item>

      <el-button type="primary" class="auth-submit" :loading="loading" @click="submit">登录</el-button>
    </el-form>

    <footer class="auth-footer">
      <RouterLink to="/register">没有账号？去注册</RouterLink>
      <RouterLink to="/forgot-password">忘记密码</RouterLink>
    </footer>
  </AuthLayout>
</template>
