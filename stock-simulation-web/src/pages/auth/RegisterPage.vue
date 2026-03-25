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
  password: '',
  nickname: '',
  initialBalance: 100000,
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
    await authStore.sendOtp(form.email)
    ElMessage.success('验证码已发送，请查收邮箱')
    startCountdown()
  } catch (error) {
    const message = error instanceof Error ? error.message : '验证码发送失败'
    ElMessage.error(message)
  } finally {
    otpLoading.value = false
  }
}

async function submit() {
  submitting.value = true
  try {
    await authStore.register({
      email: form.email,
      otp: form.otp,
      password: form.password,
      nickname: form.nickname,
      initialBalance: Number(form.initialBalance),
    })
    ElMessage.success('注册成功，已自动登录')
    await router.replace('/dashboard')
  } catch (error) {
    const message = error instanceof Error ? error.message : '注册失败，请重试'
    ElMessage.error(message)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AuthLayout>
    <header class="auth-header">
      <h1>创建模拟交易账户</h1>
      <p>完成注册后将自动创建资金账户并进入仪表盘。</p>
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

      <el-form-item label="昵称">
        <el-input v-model="form.nickname" maxlength="20" placeholder="请输入昵称" />
      </el-form-item>

      <el-form-item label="登录密码">
        <el-input v-model="form.password" type="password" show-password placeholder="至少8位，含大小写和数字" />
      </el-form-item>

      <el-form-item label="初始资金">
        <el-input-number v-model="form.initialBalance" :min="10000" :max="1000000" :step="1000" />
      </el-form-item>

      <el-button type="primary" class="auth-submit" :loading="submitting" @click="submit">注册并登录</el-button>
    </el-form>

    <footer class="auth-footer">
      <RouterLink to="/login">已有账号？去登录</RouterLink>
    </footer>
  </AuthLayout>
</template>
