import request, { unwrapResponse } from '@/api/request'
import type { UpdateUserProfileRequest, UserProfile } from '@/types/user'

export function getCurrentUserProfile(): Promise<UserProfile> {
  return unwrapResponse<UserProfile>(request.get('/user/me'))
}

export function updateCurrentUserProfile(
  payload: UpdateUserProfileRequest,
): Promise<UserProfile> {
  return unwrapResponse<UserProfile>(request.put('/user/me', payload))
}