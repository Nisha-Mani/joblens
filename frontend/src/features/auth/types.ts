export type Role = 'USER' | 'ADMIN'

export interface User {
  id: string
  email: string
  role: Role
  createdAt: string
}

export interface AuthResponse {
  token: string
  expiresAt: string
  user: User
}

export interface Session {
  token: string
  expiresAt: string
  user: User
}
