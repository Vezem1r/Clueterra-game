import type { Language } from '@/store/settingsStore'

export const DEFAULT_LANGUAGE: Language = 'en'
export const PREFIXED_LANGUAGES: Language[] = ['ru', 'cs']
export const SEO_LANGUAGES: Language[] = [DEFAULT_LANGUAGE, ...PREFIXED_LANGUAGES]
export const X_DEFAULT = 'x-default'
export const LOCALIZED_PATHS = [
  '/games',
  '/games/rivers',
  '/games/rivers/how-to',
  '/games/country-silhouettes',
  '/games/country-silhouettes/how-to',
  '/games/country-flags',
  '/games/country-flags/how-to',
  '/games/countries-quiz',
  '/games/countries-quiz/how-to',
]

const PATH_SEPARATOR = '/'
const TRAILING_SLASHES = /\/+$/

function trimmed(path: string) {
  return path.length > 1 ? path.replace(TRAILING_SLASHES, '') : path
}

export function languageOfPath(pathname: string): Language | null {
  const segment = pathname.split(PATH_SEPARATOR)[1]
  return PREFIXED_LANGUAGES.find(language => language === segment) ?? null
}

export function basePath(pathname: string): string {
  const language = languageOfPath(pathname)
  const stripped = language ? pathname.slice(language.length + 1) || PATH_SEPARATOR : pathname
  return trimmed(stripped)
}

export function isLocalizedPath(pathname: string) {
  return LOCALIZED_PATHS.includes(basePath(pathname))
}

export function localizedPath(path: string, language: Language): string {
  const base = basePath(path)
  if (language === DEFAULT_LANGUAGE || !LOCALIZED_PATHS.includes(base)) return base
  return `${PATH_SEPARATOR}${language}${base}`
}
