import type { AuthResponse, User } from '@/types'

export function authResponseToUser(response: AuthResponse): User {
  return {
    id: response.userId ?? '',
    email: response.email,
    username: response.username ?? undefined,
    role: response.role,
    enabled: true,
    createdAt: '',
    needsLegalAcceptance: response.legalAcceptanceRequired,
  }
}
