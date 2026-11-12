import { create } from 'zustand'

import { authApi } from '@/api/auth'
import { authSessionStorage } from '@/lib/browserStorage'
import { forgetTodayStates } from '@/lib/todayStateHint'
import { authResponseToUser } from '@/utils/auth'
import { extractApiErrorMessage } from '@/utils/errors'

interface TotpPending {
  email: string
  password: string
}

interface AuthState {
  user: ReturnType<typeof authResponseToUser> | null
  isAuthenticated: boolean
  isLoading: boolean
  error: string | null
  authResolved: boolean
  totpPending: TotpPending | null

  login: (email: string, password: string) => Promise<void>
  verifyTotp: (totpCode: string) => Promise<void>
  register: (email: string, username: string, password: string, termsVersion: string, privacyVersion: string) => Promise<void>
  logout: () => Promise<void>
  revalidate: () => Promise<void>
  clearError: () => void
  setUser: (user: AuthState['user']) => void
  setLegalAcceptanceRequired: (required: boolean) => void
  clearTotpPending: () => void
}

export const useAuthStore = create<AuthState>()((set, get) => ({
  user: null,
  isAuthenticated: false,
  isLoading: false,
  error: null,
  authResolved: false,
  totpPending: null,

  login: async (email: string, password: string) => {
    set({ isLoading: true, error: null, totpPending: null })
    try {
      const response = await authApi.login(email, password)

      if (response.totpRequired) {
        set({ totpPending: { email, password }, isLoading: false, authResolved: true })
        return
      }

      authSessionStorage.mark()
      set({ user: authResponseToUser(response), isAuthenticated: true, isLoading: false, authResolved: true })
    } catch (error) {
      set({ error: extractApiErrorMessage(error, 'Login failed'), isLoading: false, authResolved: true })
      throw error
    }
  },

  verifyTotp: async (totpCode: string) => {
    const pending = get().totpPending
    if (!pending) throw new Error('No TOTP pending')

    set({ isLoading: true, error: null })
    try {
      const response = await authApi.verifyLoginTotp(pending.email, pending.password, totpCode)
      authSessionStorage.mark()
      set({
        user: authResponseToUser(response),
        isAuthenticated: true,
        isLoading: false,
        authResolved: true,
        totpPending: null,
      })
    } catch (error) {
      set({ error: extractApiErrorMessage(error, 'TOTP verification failed'), isLoading: false, authResolved: true })
      throw error
    }
  },

  register: async (email: string, username: string, password: string, termsVersion: string, privacyVersion: string) => {
    set({ isLoading: true, error: null })
    try {
      const response = await authApi.register(email, username, password, termsVersion, privacyVersion)
      authSessionStorage.mark()
      set({ user: authResponseToUser(response), isAuthenticated: true, isLoading: false, authResolved: true })
    } catch (error) {
      set({ error: extractApiErrorMessage(error, 'Registration failed'), isLoading: false, authResolved: true })
      throw error
    }
  },

  revalidate: async () => {
    if (!authSessionStorage.hasSession()) {
      set({ user: null, isAuthenticated: false, authResolved: true })
      return
    }
    try {
      const data = await authApi.refresh()
      authSessionStorage.mark()
      set({ user: authResponseToUser(data), isAuthenticated: true, authResolved: true })
    } catch {
      authSessionStorage.clear()
      set({ user: null, isAuthenticated: false, totpPending: null, authResolved: true })
    }
  },

  logout: async () => {
    try {
      await authApi.logout()
    } finally {
      authSessionStorage.clear()
      forgetTodayStates()
      set({ user: null, isAuthenticated: false, totpPending: null, authResolved: true })
    }
  },

  clearError: () => set({ error: null }),
  clearTotpPending: () => set({ totpPending: null }),

  setUser: (user) => {
    if (user) {
      authSessionStorage.mark()
    } else {
      authSessionStorage.clear()
    }
    set({ user, isAuthenticated: !!user, authResolved: true })
  },
  setLegalAcceptanceRequired: (required) =>
    set((state) => ({
      user: state.user ? { ...state.user, needsLegalAcceptance: required } : state.user,
    })),
}))

const AUTH_SESSION_ATTRIBUTE = 'data-auth-session'

if (typeof window !== 'undefined') {
  useAuthStore.subscribe(({ authResolved, isAuthenticated }) => {
    if (!authResolved) return
    document.documentElement.toggleAttribute(AUTH_SESSION_ATTRIBUTE, isAuthenticated)
  })
}
