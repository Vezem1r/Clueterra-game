import { apiClient, forgetAccessExpiry, refreshSession } from './client'
import type { AuthResponse } from '@/types'

export const authApi = {
  getRegistrationStatus: async (): Promise<{ enabled: boolean }> => {
    const { data } = await apiClient.get<{ enabled: boolean }>('/auth/registration-status')
    return data
  },

  login: async (email: string, password: string): Promise<AuthResponse> => {
    const { data } = await apiClient.post<AuthResponse>('/auth/login', { email, password })
    return data
  },

  verifyLoginTotp: async (email: string, password: string, totpCode: string): Promise<AuthResponse> => {
    const { data } = await apiClient.post<AuthResponse>('/auth/verify-totp', { email, password, totpCode })
    return data
  },

  register: async (
    email: string,
    username: string,
    password: string,
    termsVersion: string,
    privacyVersion: string
  ): Promise<AuthResponse> => {
    const { data } = await apiClient.post<AuthResponse>('/auth/register', {
      email,
      username,
      password,
      termsVersion,
      privacyVersion,
    })
    return data
  },

  logout: async () => {
    try { await apiClient.delete('/auth/refresh') } catch { /* ignore */ }
    forgetAccessExpiry()
  },

  exchangeOAuthCode: async (code: string): Promise<AuthResponse> => {
    const { data } = await apiClient.post<AuthResponse>('/auth/oauth2/exchange', { code })
    return data
  },

  completeOAuthRegistration: async (
    pendingToken: string,
    username: string,
    termsVersion: string,
    privacyVersion: string
  ): Promise<AuthResponse> => {
    const { data } = await apiClient.post<AuthResponse>('/auth/oauth2/complete-registration', {
      pendingToken,
      username,
      termsVersion,
      privacyVersion,
    })
    return data
  },

  refresh: (): Promise<AuthResponse> => refreshSession(),
}
