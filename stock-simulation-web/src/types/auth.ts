export interface TokenPayload {
  accessToken: string
  refreshToken: string
  expiresIn: number
  userId: number | null
  nickname: string | null
  role?: string | null
}

export interface AuthSession {
  accessToken: string
  refreshToken: string
  expiresIn: number
  userId: number | null
  nickname: string | null
  role: string | null
}

export interface LoginRequest {
  email: string
  password: string
}

export interface RegisterRequest {
  email: string
  otp: string
  password: string
  nickname: string
  initialBalance: number
}

export interface SendOtpRequest {
  email: string
}

export interface RefreshTokenRequest {
  refreshToken: string
}

export interface ResetPasswordRequest {
  email: string
  otp: string
  newPassword: string
}
