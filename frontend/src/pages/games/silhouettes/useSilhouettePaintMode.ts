import { useCallback, useState } from 'react'
import {
  SILHOUETTE_DEFAULT_PAINT_MODE,
  SILHOUETTE_PAINT_MODES,
  SILHOUETTE_PAINT_STORAGE_KEY,
  type SilhouettePaintMode,
} from './silhouetteGameConstants'

function storedMode(): SilhouettePaintMode {
  try {
    const value = window.localStorage.getItem(SILHOUETTE_PAINT_STORAGE_KEY)
    return SILHOUETTE_PAINT_MODES.find(mode => mode === value) ?? SILHOUETTE_DEFAULT_PAINT_MODE
  } catch {
    return SILHOUETTE_DEFAULT_PAINT_MODE
  }
}

export function useSilhouettePaintMode() {
  const [mode, setMode] = useState<SilhouettePaintMode>(storedMode)
  const toggle = useCallback(() => setMode(current => {
    const next = SILHOUETTE_PAINT_MODES[(SILHOUETTE_PAINT_MODES.indexOf(current) + 1) % SILHOUETTE_PAINT_MODES.length]
    try {
      window.localStorage.setItem(SILHOUETTE_PAINT_STORAGE_KEY, next)
    } catch {
      return next
    }
    return next
  }), [])
  return { mode, toggle }
}
