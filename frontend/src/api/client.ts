import axios, { type AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import type { ApiError, AuthResponse } from '@/types'
import { authSessionStorage } from '@/lib/browserStorage'
import { useSettingsStore } from '@/store/settingsStore'
import { useToastStore } from '@/store/toastStore'

const apiClient = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
  withCredentials: true,
  paramsSerializer: { indexes: null },
})

const ACCESS_REFRESH_LEEWAY_MS = 30_000
const ADMIN_API_PREFIX = '/admin'
export const AUTH_LOGOUT_EVENT = 'auth:logout'
export const AUTH_REVALIDATE_EVENT = 'auth:revalidate'

let accessExpiresAt = 0
let refreshInFlight: Promise<AuthResponse> | null = null
let isRefreshing = false
let failedQueue: Array<{
  resolve: () => void
  reject: (err: unknown) => void
}> = []

const NON_REFRESHABLE_AUTH_PATHS = [
  '/auth/login',
  '/auth/register',
  '/auth/verify-totp',
  '/auth/oauth2/exchange',
  '/auth/oauth2/complete-registration',
  '/auth/refresh',
] as const

function processQueue(error: unknown) {
  failedQueue.forEach(({ resolve, reject }) => {
    if (!error) resolve()
    else reject(error)
  })
  failedQueue = []
}

function shouldSkipRefresh(url?: string | null) {
  return NON_REFRESHABLE_AUTH_PATHS.some(path => url?.startsWith(path))
}

function rememberAccessExpiry(expiresIn: number) {
  accessExpiresAt = expiresIn > 0 ? Date.now() + expiresIn : 0
}

function accessTokenExpiresSoon() {
  return accessExpiresAt > 0 && Date.now() >= accessExpiresAt - ACCESS_REFRESH_LEEWAY_MS
}

export function forgetAccessExpiry() {
  accessExpiresAt = 0
}

export function refreshSession(): Promise<AuthResponse> {
  if (!refreshInFlight) {
    refreshInFlight = axios.post<AuthResponse>('/api/auth/refresh', {}, { withCredentials: true })
      .then(({ data }) => {
        rememberAccessExpiry(data.expiresIn)
        return data
      })
      .catch((error: unknown) => {
        forgetAccessExpiry()
        throw error
      })
      .finally(() => {
        refreshInFlight = null
      })
  }
  return refreshInFlight
}

apiClient.interceptors.request.use(async (config: InternalAxiosRequestConfig) => {
  config.headers['Accept-Language'] = useSettingsStore.getState().language
  if (accessTokenExpiresSoon() && authSessionStorage.hasSession() && !shouldSkipRefresh(config.url)) {
    await refreshSession().catch(() => undefined)
  }
  return config
})

apiClient.interceptors.response.use(
  (response: AxiosResponse) => {
    const expiresIn = (response.data as Partial<AuthResponse> | undefined)?.expiresIn
    if (shouldSkipRefresh(response.config.url) && typeof expiresIn === 'number') {
      rememberAccessExpiry(expiresIn)
    }
    return response
  },
  async (error: AxiosError<ApiError>) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean }
    const legalErrorCode = error.response?.data?.code

    if (
      error.response?.status === 401
      && authSessionStorage.hasSession()
      && !originalRequest._retry
      && !shouldSkipRefresh(originalRequest?.url)
    ) {
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({
            resolve: () => resolve(apiClient(originalRequest)),
            reject,
          })
        })
      }

      originalRequest._retry = true
      isRefreshing = true

      try {
        await refreshSession()
        processQueue(null)
        return apiClient(originalRequest)
      } catch (refreshError) {
        processQueue(refreshError)
        useToastStore.getState().addToast({ type: 'error', message: 'Session expired. Please log in again.' })
        window.dispatchEvent(new Event(AUTH_LOGOUT_EVENT))
        return Promise.reject(refreshError)
      } finally {
        isRefreshing = false
      }
    }

    if (error.response?.status === 401 && originalRequest?._retry && authSessionStorage.hasSession()) {
      window.dispatchEvent(new Event(AUTH_LOGOUT_EVENT))
    }

    if (error.response?.status === 403 && legalErrorCode === 'LEGAL_ACCEPTANCE_REQUIRED') {
      window.dispatchEvent(new Event('auth:legal-required'))
    } else if (error.response?.status === 403 && originalRequest?.url?.startsWith(ADMIN_API_PREFIX)) {
      window.dispatchEvent(new Event(AUTH_REVALIDATE_EVENT))
    }

    return Promise.reject(error)
  }
)

export { apiClient }
