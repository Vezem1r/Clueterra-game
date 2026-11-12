import { useSyncExternalStore } from 'react'

import { CONSENT_UPDATED_EVENT, consentStorage } from '@/lib/browserStorage'

function subscribeToConsent(onChange: () => void) {
  const onStorage = () => {
    consentStorage.syncAttribute()
    onChange()
  }
  window.addEventListener(CONSENT_UPDATED_EVENT, onChange)
  window.addEventListener('storage', onStorage)

  return () => {
    window.removeEventListener(CONSENT_UPDATED_EVENT, onChange)
    window.removeEventListener('storage', onStorage)
  }
}

export function useCookieConsent() {
  return useSyncExternalStore(
    subscribeToConsent,
    consentStorage.hasAccepted,
    () => true,
  )
}
