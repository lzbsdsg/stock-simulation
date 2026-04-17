export interface UserProfile {
  userId: number
  email: string
  nickname: string
  role: string
  status: string
}

export interface UpdateUserProfileRequest {
  nickname: string
}