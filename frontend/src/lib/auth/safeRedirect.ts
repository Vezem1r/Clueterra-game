export const DEFAULT_REDIRECT = '/'

const REDIRECT_BASE = 'https://clueterra.invalid'

export function safeRedirectPath(value: string | null): string {
  if (!value?.startsWith('/')) {
    return DEFAULT_REDIRECT
  }
  try {
    const url = new URL(value, REDIRECT_BASE)
    return url.origin === REDIRECT_BASE
      ? `${url.pathname}${url.search}${url.hash}`
      : DEFAULT_REDIRECT
  } catch {
    return DEFAULT_REDIRECT
  }
}
