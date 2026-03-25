import type { AuthSession } from '@/types/auth'

const ACCESS_TOKEN_KEY = 'ss_access_token'
const REFRESH_TOKEN_KEY = 'ss_refresh_token'
const USER_ID_KEY = 'ss_user_id'
const NICKNAME_KEY = 'ss_user_nickname'
const EXPIRES_IN_KEY = 'ss_expires_in'

export const AUTH_SESSION_EVENT = 'stock-auth-session-changed'

function dispatchSessionEvent() {
  if (typeof window === 'undefined') {
    return
  }
  window.dispatchEvent(new CustomEvent(AUTH_SESSION_EVENT))
}

export function saveAuthSession(session: AuthSession): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, session.accessToken)
  localStorage.setItem(REFRESH_TOKEN_KEY, session.refreshToken)
  localStorage.setItem(EXPIRES_IN_KEY, String(session.expiresIn))

  if (session.userId !== null) {
    localStorage.setItem(USER_ID_KEY, String(session.userId))
  } else {
    localStorage.removeItem(USER_ID_KEY)
  }

  if (session.nickname) {
    localStorage.setItem(NICKNAME_KEY, session.nickname)
  } else {
    localStorage.removeItem(NICKNAME_KEY)
  }

  dispatchSessionEvent()
}

export function clearAuthSession(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
  localStorage.removeItem(USER_ID_KEY)
  localStorage.removeItem(NICKNAME_KEY)
  localStorage.removeItem(EXPIRES_IN_KEY)
  dispatchSessionEvent()
}

export function loadAuthSession(): AuthSession | null {
  const accessToken = localStorage.getItem(ACCESS_TOKEN_KEY)
  const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY)
  if (!accessToken || !refreshToken) {
    return null
  }

  const userIdRaw = localStorage.getItem(USER_ID_KEY)
  const nicknameRaw = localStorage.getItem(NICKNAME_KEY)
  const expiresInRaw = localStorage.getItem(EXPIRES_IN_KEY)

  const userId = userIdRaw ? Number(userIdRaw) : null
  const expiresIn = expiresInRaw ? Number(expiresInRaw) : 0

  return {
    accessToken,
    refreshToken,
    userId: Number.isFinite(userId) ? userId : null,
    nickname: nicknameRaw,
    expiresIn: Number.isFinite(expiresIn) ? expiresIn : 0,
  }
}

export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_TOKEN_KEY)
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_TOKEN_KEY)
}
