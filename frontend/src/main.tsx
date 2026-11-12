import { StrictMode } from 'react'
import { createRoot, hydrateRoot } from 'react-dom/client'
import './index.css'
import App from './App'
import { preloadPrerenderedPage } from './lib/pageLoaders'
import { languageOfPath } from './lib/locale/localePaths'
import { useSettingsStore, type Language } from './store/settingsStore'
import { loadLanguage } from './i18n'
import type { ProjectSsrData } from './features/projects/projectTypes'

const PRELOAD_RECOVERY_KEY = 'clueterra:preload-recovery'
const PRELOAD_RECOVERY_WINDOW_MS = 60_000

interface PreloadRecoveryMarker {
  url: string
  attemptedAt: number
}

function readPreloadRecoveryMarker(): PreloadRecoveryMarker | null {
  const value = window.sessionStorage.getItem(PRELOAD_RECOVERY_KEY)
  if (!value) return null

  try {
    const marker = JSON.parse(value) as Partial<PreloadRecoveryMarker>
    if (typeof marker.url === 'string' && typeof marker.attemptedAt === 'number') {
      return {
        url: marker.url,
        attemptedAt: marker.attemptedAt,
      }
    }
  } catch {
    // Replace malformed recovery state below.
  }

  window.sessionStorage.removeItem(PRELOAD_RECOVERY_KEY)
  return null
}

window.addEventListener('vite:preloadError', (event) => {
  try {
    const now = Date.now()
    const currentUrl = window.location.href
    const previousAttempt = readPreloadRecoveryMarker()
    const recentlyRetried = previousAttempt?.url === currentUrl
      && now - previousAttempt.attemptedAt < PRELOAD_RECOVERY_WINDOW_MS

    if (recentlyRetried) return

    window.sessionStorage.setItem(PRELOAD_RECOVERY_KEY, JSON.stringify({
      url: currentUrl,
      attemptedAt: now,
    } satisfies PreloadRecoveryMarker))

    event.preventDefault()
    window.location.reload()
  } catch {
    // Storage is unavailable, so let the original import error propagate.
  }
})

const root = document.getElementById('root')

if (!root) {
  throw new Error('Application root was not found')
}

function readProjectSsrData(): ProjectSsrData | null {
  const element = document.getElementById('project-ssr-data')
  if (!element?.textContent) return null

  try {
    return JSON.parse(element.textContent) as ProjectSsrData
  } catch {
    return null
  }
}

const SETTINGS_STORAGE_KEY = 'clueterra-settings'
const pathLanguage = languageOfPath(window.location.pathname)
if (pathLanguage) useSettingsStore.setState({ language: pathLanguage })

function storedLanguage(): Language | null {
  try {
    const stored = JSON.parse(window.localStorage.getItem(SETTINGS_STORAGE_KEY) ?? 'null') as { state?: { language?: Language } } | null
    return stored?.state?.language ?? null
  } catch {
    return null
  }
}

const storedPreference = storedLanguage()
if (!pathLanguage && storedPreference) void loadLanguage(storedPreference).catch(() => undefined)
const pathLanguageReady = pathLanguage ? loadLanguage(pathLanguage).catch(() => undefined) : Promise.resolve()

const application = (
  <StrictMode>
    <App initialProjectData={readProjectSsrData()} />
  </StrictMode>
)

if (root.dataset.renderMode === 'ssg' || root.dataset.renderMode === 'ssr') {
  void Promise.all([preloadPrerenderedPage(window.location.pathname).catch(() => undefined), pathLanguageReady])
    .finally(() => hydrateRoot(root, application))
} else {
  createRoot(root).render(application)
}
