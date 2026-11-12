import { useEffect } from 'react'
import { getApiErrorCode } from '@/utils/errors'

const ROUND_NOT_CURRENT = 'ROUND_NOT_CURRENT'
const VISIBLE = 'visible'

export function isStaleRound(error: unknown) {
  return getApiErrorCode(error) === ROUND_NOT_CURRENT
}

export function useRoundSync(enabled: boolean, sync: () => void) {
  useEffect(() => {
    if (!enabled) return
    const onVisible = () => {
      if (document.visibilityState === VISIBLE) sync()
    }
    document.addEventListener('visibilitychange', onVisible)
    return () => document.removeEventListener('visibilitychange', onVisible)
  }, [enabled, sync])
}
