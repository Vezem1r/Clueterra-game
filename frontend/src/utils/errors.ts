import { isAxiosError } from 'axios'

const TRAILING_SENTENCE_PUNCTUATION = /[.!?…]+$/

interface ApiErrorData {
  message?: string
  error?: string
  code?: string
  errors?: Record<string, string>
  retryAfterSeconds?: number
}

export function extractApiErrorMessage(err: unknown, fallback: string): string {
  if (!isAxiosError(err)) return err instanceof Error ? err.message : fallback
  const data = err.response?.data as ApiErrorData | undefined
  if (err.response?.status === 429) {
    const retryAfterSeconds = extractRetryAfterSeconds(err, data)
    if (retryAfterSeconds !== null) {
      return `${fallback.replace(TRAILING_SENTENCE_PUNCTUATION, '')}. Please try again in ${formatRetryAfter(retryAfterSeconds)}.`
    }
  }
  if (!data) return fallback
  if (data.errors && typeof data.errors === 'object') {
    return Object.entries(data.errors).map(([f, m]) => `${f}: ${m}`).join('; ')
  }
  return data.message || data.error || fallback
}

export function extractFieldErrors(err: unknown): Record<string, string> | null {
  if (!isAxiosError(err)) return null
  const data = err.response?.data as ApiErrorData | undefined
  if (data?.errors && typeof data.errors === 'object') return data.errors
  return null
}

export function getApiErrorCode(err: unknown): string | null {
  if (!isAxiosError(err)) return null
  const data = err.response?.data as ApiErrorData | undefined
  return typeof data?.code === 'string' ? data.code : null
}

function extractRetryAfterSeconds(err: unknown, data?: ApiErrorData): number | null {
  if (!isAxiosError(err)) return null
  if (typeof data?.retryAfterSeconds === 'number' && Number.isFinite(data.retryAfterSeconds)) {
    return Math.max(1, Math.ceil(data.retryAfterSeconds))
  }
  const retryAfterHeader = err.response?.headers?.['retry-after']
  const retryAfterValue = Array.isArray(retryAfterHeader) ? retryAfterHeader[0] : retryAfterHeader
  if (typeof retryAfterValue === 'string') {
    const parsed = Number.parseInt(retryAfterValue, 10)
    if (Number.isFinite(parsed) && parsed > 0) {
      return parsed
    }
  }
  return null
}

function formatRetryAfter(seconds: number): string {
  if (seconds < 60) {
    return `${seconds}s`
  }
  const minutes = Math.floor(seconds / 60)
  const remainingSeconds = seconds % 60
  if (remainingSeconds === 0) {
    return `${minutes}m`
  }
  return `${minutes}m ${remainingSeconds}s`
}
