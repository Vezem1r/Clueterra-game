import { useCallback } from 'react'
import { useLocation } from 'react-router-dom'
import { languageOfPath, localizedPath } from '@/lib/locale/localePaths'

export function useLocalizedPath() {
  const { pathname } = useLocation()
  const language = languageOfPath(pathname)
  return useCallback((path: string) => (language ? localizedPath(path, language) : path), [language])
}
