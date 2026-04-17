import type { AuthSession } from '@/types/auth'

const ACCESS_TOKEN_KEY = 'ss_access_token'
const REFRESH_TOKEN_KEY = 'ss_refresh_token'
const USER_ID_KEY = 'ss_user_id'
const NICKNAME_KEY = 'ss_user_nickname'
const EXPIRES_IN_KEY = 'ss_expires_in'
const USER_ROLE_KEY = 'ss_user_role'

export const AUTH_SESSION_EVENT = 'stock-auth-session-changed'

type StorageLike = {
  getItem: (key: string) => string | null
  setItem: (key: string, value: string) => void
  removeItem: (key: string) => void
}

const memoryStorage = new Map<string, string>()

function dispatchSessionEvent() {
  if (typeof window === 'undefined') {
    return
  }
  window.dispatchEvent(new CustomEvent(AUTH_SESSION_EVENT))
}

export function saveAuthSession(session: AuthSession): void {
  setValue(ACCESS_TOKEN_KEY, session.accessToken)
  setValue(REFRESH_TOKEN_KEY, session.refreshToken)
  setValue(EXPIRES_IN_KEY, String(session.expiresIn))

  if (session.userId !== null) {
    setValue(USER_ID_KEY, String(session.userId))
  } else {
    removeValue(USER_ID_KEY)
  }

  if (session.nickname) {
    setValue(NICKNAME_KEY, session.nickname)
  } else {
    removeValue(NICKNAME_KEY)
  }

  if (session.role) {
    setValue(USER_ROLE_KEY, session.role)
  } else {
    removeValue(USER_ROLE_KEY)
  }

  dispatchSessionEvent()
}

export function clearAuthSession(): void {
  removeValue(ACCESS_TOKEN_KEY)
  removeValue(REFRESH_TOKEN_KEY)
  removeValue(USER_ID_KEY)
  removeValue(NICKNAME_KEY)
  removeValue(EXPIRES_IN_KEY)
  removeValue(USER_ROLE_KEY)
  dispatchSessionEvent()
}

export function loadAuthSession(): AuthSession | null {
  const accessToken = getValue(ACCESS_TOKEN_KEY)
  const refreshToken = getValue(REFRESH_TOKEN_KEY)
  if (!accessToken || !refreshToken) {
    return null
  }

  const userIdRaw = getValue(USER_ID_KEY)
  const nicknameRaw = getValue(NICKNAME_KEY)
  const expiresInRaw = getValue(EXPIRES_IN_KEY)
  const roleRaw = getValue(USER_ROLE_KEY)

  const userId = userIdRaw ? Number(userIdRaw) : null
  const expiresIn = expiresInRaw ? Number(expiresInRaw) : 0
  const role = normalizeRole(roleRaw ?? parseAccessTokenRole(accessToken))

  return {
    accessToken,
    refreshToken,
    userId: Number.isFinite(userId) ? userId : null,
    nickname: nicknameRaw,
    expiresIn: Number.isFinite(expiresIn) ? expiresIn : 0,
    role,
  }
}

export function getAccessToken(): string | null {
  return getValue(ACCESS_TOKEN_KEY)
}

export function getRefreshToken(): string | null {
  return getValue(REFRESH_TOKEN_KEY)
}

export function parseAccessTokenRole(accessToken: string): string | null {
  if (!accessToken) {
    return null
  }

  const firstDot = accessToken.indexOf('.')
  if (firstDot <= 0) {
    return null
  }

  const payloadPart = accessToken.slice(0, firstDot)
  const decoded = decodeBase64Url(payloadPart)
  if (!decoded) {
    return null
  }

  const parts = decoded.split('|')
  if (parts.length < 3) {
    return null
  }

  return normalizeRole(parts[2])
}

function normalizeRole(value: string | null | undefined): string | null {
  if (!value) {
    return null
  }
  const normalized = value.trim().toUpperCase()
  if (!normalized || normalized === '-') {
    return null
  }
  return normalized
}

function decodeBase64Url(input: string): string | null {
  if (!input) {
    return null
  }

  const base64 = toBase64(input)
  try {
    if (typeof atob === 'function') {
      return atob(base64)
    }
  } catch (_error) {
    return null
  }

  return null
}

function toBase64(input: string): string {
  const normalized = input.replace(/-/g, '+').replace(/_/g, '/')
  const remainder = normalized.length % 4
  if (remainder === 0) {
    return normalized
  }
  return `${normalized}${'='.repeat(4 - remainder)}`
}

function getStorage(): StorageLike | null {
  const storage = (globalThis as { localStorage?: Partial<StorageLike> }).localStorage
  if (!storage) {
    return null
  }
  if (
    typeof storage.getItem !== 'function' ||
    typeof storage.setItem !== 'function' ||
    typeof storage.removeItem !== 'function'
  ) {
    return null
  }
  return storage as StorageLike
}

function getValue(key: string): string | null {
  const storage = getStorage()
  if (storage) {
    return storage.getItem(key)
  }
  return memoryStorage.get(key) ?? null
}

function setValue(key: string, value: string): void {
  const storage = getStorage()
  if (storage) {
    storage.setItem(key, value)
    return
  }
  memoryStorage.set(key, value)
}

function removeValue(key: string): void {
  const storage = getStorage()
  if (storage) {
    storage.removeItem(key)
    return
  }
  memoryStorage.delete(key)
}
