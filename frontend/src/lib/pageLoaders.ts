import type { ComponentType } from 'react'
import { basePath } from '@/lib/locale/localePaths'

export interface PageModule<P> {
  default: ComponentType<P>
}

export interface PageLoader<P> {
  (): Promise<PageModule<P>>
  loaded: () => PageModule<P> | undefined
}

function pageLoader<P>(importPage: () => Promise<PageModule<P>>): PageLoader<P> {
  let pageModule: PageModule<P> | undefined
  const load = (() =>
    importPage().then((loadedModule) => {
      pageModule = loadedModule
      return loadedModule
    })) as PageLoader<P>
  load.loaded = () => pageModule
  return load
}

export const loadHomePage = pageLoader(() =>
  import('@/pages/game/home/HomePage').then((m) => ({ default: m.HomePage })),
)

export const loadGamesPage = pageLoader(() =>
  import('@/pages/games/GamesPage').then((m) => ({ default: m.GamesPage })),
)

export const loadRiversGamePage = pageLoader(() =>
  import('@/pages/games/RiversGamePage').then((m) => ({ default: m.RiversGamePage })),
)

export const loadRiversHowToPage = pageLoader(() =>
  import('@/pages/games/RiversHowToPage').then((m) => ({ default: m.RiversHowToPage })),
)

export const loadCountrySilhouettesPage = pageLoader(() =>
  import('@/pages/games/CountrySilhouettesPage').then((m) => ({ default: m.CountrySilhouettesPage })),
)

export const loadCountryFlagsPage = pageLoader(() =>
  import('@/pages/games/CountryFlagsPage').then((m) => ({ default: m.CountryFlagsPage })),
)

export const loadCountryFlagsHowToPage = pageLoader(() =>
  import('@/pages/games/CountryFlagsHowToPage').then((m) => ({ default: m.CountryFlagsHowToPage })),
)

export const loadCountriesQuizPage = pageLoader(() =>
  import('@/pages/games/CountriesQuizPage').then((m) => ({ default: m.CountriesQuizPage })),
)

export const loadCountriesQuizHowToPage = pageLoader(() =>
  import('@/pages/games/CountriesQuizHowToPage').then((m) => ({ default: m.CountriesQuizHowToPage })),
)

export const loadCountrySilhouettesHowToPage = pageLoader(() =>
  import('@/pages/games/CountrySilhouettesHowToPage').then((m) => ({ default: m.CountrySilhouettesHowToPage })),
)

export const loadBudgetGamePage = pageLoader(() =>
  import('@/pages/budget/BudgetGamePage').then((m) => ({
    default: m.BudgetGamePage,
  })),
)

export const loadBudgetHowToPage = pageLoader(() =>
  import('@/pages/public/BudgetHowToPage').then((m) => ({
    default: m.BudgetHowToPage,
  })),
)

export const loadNotFoundPage = pageLoader(() =>
  import('@/pages/NotFoundPage').then((m) => ({ default: m.NotFoundPage })),
)

export const loadPrivacyPage = pageLoader(() =>
  import('@/pages/legal/PrivacyPage').then((m) => ({
    default: m.PrivacyPage,
  })),
)

export const loadTermsPage = pageLoader(() =>
  import('@/pages/legal/TermsPage').then((m) => ({ default: m.TermsPage })),
)

export const loadDataSourcesPage = pageLoader(() =>
  import('@/pages/legal/DataSourcesPage').then((m) => ({
    default: m.DataSourcesPage,
  })),
)

export const loadAboutPage = pageLoader(() =>
  import('@/pages/public/AboutPage').then((m) => ({
    default: m.AboutPage,
  })),
)

export const loadHowToPage = pageLoader(() =>
  import('@/pages/public/HowToPage').then((m) => ({
    default: m.HowToPage,
  })),
)

export const loadProjectDirectoryPage = pageLoader(() =>
  import('@/pages/projects/ProjectDirectoryPage').then((m) => ({
    default: m.ProjectDirectoryPage,
  })),
)

export const loadProjectHubPage = pageLoader(() =>
  import('@/pages/projects/ProjectHubPage').then((m) => ({
    default: m.ProjectHubPage,
  })),
)

export const loadProjectDetailPage = pageLoader(() =>
  import('@/pages/projects/ProjectDetailPage').then((m) => ({
    default: m.ProjectDetailPage,
  })),
)

const PRERENDERED_PAGE_LOADERS: Array<[RegExp, () => Promise<unknown>]> = [
  [/^\/$/, loadHomePage],
  [/^\/games\/?$/, loadGamesPage],
  [/^\/games\/rivers\/?$/, loadRiversGamePage],
  [/^\/games\/rivers\/how-to\/?$/, loadRiversHowToPage],
  [/^\/games\/country-silhouettes\/?$/, loadCountrySilhouettesPage],
  [/^\/games\/country-silhouettes\/how-to\/?$/, loadCountrySilhouettesHowToPage],
  [/^\/games\/country-flags\/?$/, loadCountryFlagsPage],
  [/^\/games\/country-flags\/how-to\/?$/, loadCountryFlagsHowToPage],
  [/^\/games\/countries-quiz\/?$/, loadCountriesQuizPage],
  [/^\/games\/countries-quiz\/how-to\/?$/, loadCountriesQuizHowToPage],
  [/^\/about\/?$/, loadAboutPage],
  [/^\/how-to\/?$/, loadHowToPage],
  [/^\/budget\/?$/, loadBudgetGamePage],
  [/^\/budget\/how-to\/?$/, loadBudgetHowToPage],
  [/^\/terms\/?$/, loadTermsPage],
  [/^\/privacy\/?$/, loadPrivacyPage],
  [/^\/data-sources\/?$/, loadDataSourcesPage],
  [/^\/projects(?:\/page\/\d+)?\/?$/, loadProjectDirectoryPage],
  [/^\/projects\/most-expensive(?:\/[a-z0-9-]+)?\/?$/, loadProjectHubPage],
  [/^\/projects\/countries\/[a-z0-9-]+\/?$/, loadProjectHubPage],
  [/^\/projects\/[^/]+(?:\/[^/]+)?\/?$/, loadProjectDetailPage],
]

export function preloadPrerenderedPage(pathname: string): Promise<unknown> {
  const match = PRERENDERED_PAGE_LOADERS.find(([pattern]) => pattern.test(basePath(pathname)))
  return (match ? match[1] : loadNotFoundPage)()
}
