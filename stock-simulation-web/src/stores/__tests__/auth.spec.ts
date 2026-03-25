import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import { clearAuthSession, loadAuthSession } from '@/utils/auth-storage'

vi.mock('@/api/auth', () => ({
  sendOtp: vi.fn(),
  login: vi.fn(),
  register: vi.fn(),
  refreshToken: vi.fn(),
  forgotPassword: vi.fn(),
  resetPassword: vi.fn(),
  logout: vi.fn(),
}))

import * as authApi from '@/api/auth'

describe('useAuthStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    clearAuthSession()
    vi.clearAllMocks()
  })

  it('should hydrate from local storage', () => {
    const store = useAuthStore()

    store.applyTokenPayload({
      accessToken: 'token-a',
      refreshToken: 'token-r',
      expiresIn: 3600,
      userId: 101,
      nickname: 'tester',
    })

    setActivePinia(createPinia())
    const nextStore = useAuthStore()
    nextStore.ensureInitialized()

    expect(nextStore.isAuthenticated).toBe(true)
    expect(nextStore.accessToken).toBe('token-a')
    expect(nextStore.userId).toBe(101)
    expect(nextStore.nickname).toBe('tester')
  })

  it('should clear local session when logout called', async () => {
    const mockedLogout = vi.mocked(authApi.logout)
    mockedLogout.mockResolvedValue(undefined)

    const store = useAuthStore()
    store.applyTokenPayload({
      accessToken: 'token-a',
      refreshToken: 'token-r',
      expiresIn: 3600,
      userId: 1,
      nickname: 'name',
    })

    await store.logout()

    expect(store.isAuthenticated).toBe(false)
    expect(loadAuthSession()).toBeNull()
    expect(mockedLogout).toHaveBeenCalledTimes(1)
  })
})
