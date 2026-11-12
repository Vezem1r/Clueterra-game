import { safeRedirectPath } from '@/lib/auth/safeRedirect'

const AUTH_SESSION_KEY = 'clu_auth_session'

export const authSessionStorage = {
  mark: () => {
    localStorage.setItem(AUTH_SESSION_KEY, '1')
  },

  clear: () => {
    localStorage.removeItem(AUTH_SESSION_KEY)
  },

  hasSession: () => localStorage.getItem(AUTH_SESSION_KEY) === '1',
}

const CONSENT_KEY = 'clueterra_cookieConsent'
const CONSENT_ATTRIBUTE = 'data-cookie-consent'
export const CONSENT_UPDATED_EVENT = 'clueterra:cookie-consent-updated'

export const consentStorage = {
  hasAccepted: () => typeof localStorage !== 'undefined' && localStorage.getItem(CONSENT_KEY) === '1',
  syncAttribute: () => {
    document.documentElement.toggleAttribute(CONSENT_ATTRIBUTE, consentStorage.hasAccepted())
  },
  accept: () => {
    localStorage.setItem(CONSENT_KEY, '1')
    if (typeof window !== 'undefined') {
      consentStorage.syncAttribute()
      window.dispatchEvent(new Event(CONSENT_UPDATED_EVENT))
    }
  },
}

const OAUTH_REDIRECT_KEY = 'clueterra.oauth.redirect'

export const oauthRedirectStorage = {
  save: (path: string) => {
    sessionStorage.setItem(OAUTH_REDIRECT_KEY, path)
  },

  consume: () => {
    const stored = sessionStorage.getItem(OAUTH_REDIRECT_KEY)
    sessionStorage.removeItem(OAUTH_REDIRECT_KEY)
    return safeRedirectPath(stored)
  },
}

const RIVER_DRAFT_KEY_PREFIX = 'clueterra:river-draft:v1'

function key(playerId: string | number, questionId: number) {
  return `${RIVER_DRAFT_KEY_PREFIX}:${playerId}:${questionId}`
}

export const riverDraftStorage = {
  read(playerId: string | number, questionId: number): string {
    try {
      return window.localStorage.getItem(key(playerId, questionId)) ?? ''
    } catch {
      return ''
    }
  },

  write(playerId: string | number, questionId: number, value: string) {
    try {
      if (value) {
        window.localStorage.setItem(key(playerId, questionId), value)
      } else {
        window.localStorage.removeItem(key(playerId, questionId))
      }
    } catch {
      return
    }
  },
}
