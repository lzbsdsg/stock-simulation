import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import * as authApi from '@/api/auth'
import type { AuthSession, LoginRequest, RegisterRequest, TokenPayload } from '@/types/auth'
import {
  AUTH_SESSION_EVENT,
  clearAuthSession,
  loadAuthSession,
  parseAccessTokenRole,
  saveAuthSession,
} from '@/utils/auth-storage'

function toSession(payload: TokenPayload): AuthSession {
  return {
    accessToken: payload.accessToken,
    refreshToken: payload.refreshToken,
    expiresIn: payload.expiresIn,
    userId: payload.userId,
    nickname: payload.nickname,
    role: payload.role ?? parseAccessTokenRole(payload.accessToken),
  }
}

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref('')
  const refreshToken = ref('')
  const expiresIn = ref(0)
  const userId = ref<number | null>(null)
  const nickname = ref<string | null>(null)
  const role = ref<string | null>(null)
  const initialized = ref(false)

  const isAuthenticated = computed(() => accessToken.value.length > 0)
  const isAdmin = computed(() => role.value === 'ADMIN')

  let eventBound = false

  function applySession(session: AuthSession | null): void {
    if (!session) {
      accessToken.value = ''
      refreshToken.value = ''
      expiresIn.value = 0
      userId.value = null
      nickname.value = null
      role.value = null
      return
    }

    accessToken.value = session.accessToken
    refreshToken.value = session.refreshToken
    expiresIn.value = session.expiresIn
    userId.value = session.userId
    nickname.value = session.nickname
    role.value = session.role
  }

  function hydrateFromStorage(): void {
    applySession(loadAuthSession())
  }

  function bindSessionEvents(): void {
    if (eventBound || typeof window === 'undefined') {
      return
    }

    window.addEventListener(AUTH_SESSION_EVENT, hydrateFromStorage)
    eventBound = true
  }

  function ensureInitialized(): void {
    if (initialized.value) {
      return
    }

    hydrateFromStorage()
    bindSessionEvents()
    initialized.value = true
  }

  function applyTokenPayload(payload: TokenPayload): void {
    const session = toSession(payload)
    saveAuthSession(session)
    applySession(session)
  }

  async function sendOtp(email: string): Promise<void> {
    await authApi.sendOtp({ email })
  }

  async function login(payload: LoginRequest): Promise<void> {
    const token = await authApi.login(payload)
    applyTokenPayload(token)
  }

  async function register(payload: RegisterRequest): Promise<void> {
    const token = await authApi.register(payload)
    applyTokenPayload(token)
  }

  async function forgotPassword(email: string): Promise<void> {
    await authApi.forgotPassword({ email })
  }

  async function resetPassword(email: string, otp: string, newPassword: string): Promise<void> {
    await authApi.resetPassword({ email, otp, newPassword })
  }

  async function refreshAccessToken(): Promise<string | null> {
    ensureInitialized()
    if (!refreshToken.value) {
      return null
    }

    const token = await authApi.refreshToken({ refreshToken: refreshToken.value })
    applyTokenPayload(token)
    return token.accessToken
  }

  async function logout(): Promise<void> {
    try {
      if (isAuthenticated.value) {
        await authApi.logout()
      }
    } finally {
      clearAuthSession()
      applySession(null)
    }
  }

  return {
    accessToken,
    refreshToken,
    expiresIn,
    userId,
    nickname,
    role,
    initialized,
    isAuthenticated,
    isAdmin,
    ensureInitialized,
    hydrateFromStorage,
    applyTokenPayload,
    sendOtp,
    login,
    register,
    forgotPassword,
    resetPassword,
    refreshAccessToken,
    logout,
  }
})
