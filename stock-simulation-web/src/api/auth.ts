import request, { unwrapResponse } from '@/api/request'
import type {
  LoginRequest,
  RefreshTokenRequest,
  RegisterRequest,
  ResetPasswordRequest,
  SendOtpRequest,
  TokenPayload,
} from '@/types/auth'

export function sendOtp(payload: SendOtpRequest): Promise<void> {
  return unwrapResponse(request.post('/auth/otp/send', payload))
}

export function login(payload: LoginRequest): Promise<TokenPayload> {
  return unwrapResponse(request.post('/auth/login', payload))
}

export function register(payload: RegisterRequest): Promise<TokenPayload> {
  return unwrapResponse(request.post('/auth/register', payload))
}

export function refreshToken(payload: RefreshTokenRequest): Promise<TokenPayload> {
  return unwrapResponse(request.post('/auth/refresh', payload))
}

export function logout(): Promise<void> {
  return unwrapResponse(request.post('/auth/logout'))
}

export function forgotPassword(payload: SendOtpRequest): Promise<void> {
  return unwrapResponse(request.post('/auth/forgot-password', payload))
}

export function resetPassword(payload: ResetPasswordRequest): Promise<void> {
  return unwrapResponse(request.post('/auth/reset-password', payload))
}
