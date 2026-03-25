<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AuthLayout from '@/layouts/AuthLayout.vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const form = reactive({
  email: '',
  otp: '',
  newPassword: '',
})

const submitting = ref(false)
const otpLoading = ref(false)
const otpCountDown = ref(0)
let countdownTimer: number | null = null

function startCountdown() {
  otpCountDown.value = 60
  if (countdownTimer !== null) {
    window.clearInterval(countdownTimer)
  }

  countdownTimer = window.setInterval(() => {
    otpCountDown.value -= 1
    if (otpCountDown.value <= 0) {
      if (countdownTimer !== null) {
        window.clearInterval(countdownTimer)
      }
      countdownTimer = null
    }
  }, 1000)
}

async function handleSendOtp() {
  if (!form.email) {
    ElMessage.warning('请先填写邮箱地址')
    return
  }

  otpLoading.value = true
  try {
    await authStore.forgotPassword(form.email)
    ElMessage.success('重置验证码已发送')
    startCountdown()
  } catch (error) {
    const message = error instanceof Error ? error.message : '发送失败'
    ElMessage.error(message)
  } finally {
    otpLoading.value = false
  }
}

async function submit() {
  submitting.value = true
  try {
    await authStore.resetPassword(form.email, form.otp, form.newPassword)
    ElMessage.success('密码重置成功，请重新登录')
    await router.replace('/login')
  } catch (error) {
    const message = error instanceof Error ? error.message : '密码重置失败'
    ElMessage.error(message)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AuthLayout>
    <header class="auth-header">
      <h1>重置登录密码</h1>
      <p>通过邮箱验证码重设账户密码。</p>
    </header>

    <el-form class="auth-form" :model="form" label-position="top" @submit.prevent="submit">
      <el-form-item label="邮箱">
        <el-input v-model="form.email" type="email" autocomplete="email" placeholder="you@example.com" />
      </el-form-item>

      <el-form-item label="验证码">
        <div class="otp-row">
          <el-input v-model="form.otp" maxlength="6" placeholder="6位验证码" />
          <el-button :disabled="otpCountDown > 0" :loading="otpLoading" @click="handleSendOtp">
            {{ otpCountDown > 0 ? `${otpCountDown}s` : '发送验证码' }}
          </el-button>
        </div>
      </el-form-item>

      <el-form-item label="新密码">
        <el-input
          v-model="form.newPassword"
          type="password"
          show-password
          placeholder="请输入新的登录密码"
        />
      </el-form-item>

      <el-button type="primary" class="auth-submit" :loading="submitting" @click="submit">确认重置</el-button>
    </el-form>

    <footer class="auth-footer">
      <RouterLink to="/login">返回登录</RouterLink>
    </footer>
  </AuthLayout>
</template>
