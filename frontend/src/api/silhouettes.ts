import { apiClient } from './client'

export type SilhouetteLocale = 'en' | 'ru' | 'cs'
export type SilhouetteNames = Record<SilhouetteLocale, string>
export type SilhouettePoint = [number, number]
export type SilhouetteBounds = [number, number, number, number]

export interface SilhouetteFlagColor {
  color: string
  share: number
}

export interface SilhouetteRegionHint {
  region: string
  subregion: string
  center: SilhouettePoint
}

export interface SilhouetteLocationHint {
  center: SilhouettePoint
  bounds: SilhouetteBounds
}

export interface SilhouetteLandmarkHint {
  url: string
  authorName: string | null
  licenseLabel: string | null
  licenseUrl: string | null
}

export interface SilhouetteHints {
  region: SilhouetteRegionHint | null
  location: SilhouetteLocationHint | null
  capitals: SilhouetteNames[] | null
  flagColors: SilhouetteFlagColor[] | null
  landmark: SilhouetteLandmarkHint | null
}

export type SilhouetteClueKind = 'REGION' | 'LOCATION' | 'CAPITAL' | 'LANDMARK'

export interface SilhouetteClue {
  kind: SilhouetteClueKind
  costXp: number
}

export interface SilhouetteQuestion {
  round: number
  tier: number
  shapePath: string
  hints: SilhouetteHints
  clues: SilhouetteClue[]
  hintsUsed: number
  attempts: number
  maximumXp: number
  baseXp: number
  misses: SilhouetteMiss[]
}

export interface SilhouetteRank {
  rank: number
  direction: 'LARGEST' | 'SMALLEST'
}

export interface SilhouetteFacts {
  capital: SilhouetteNames | null
  population: { value: number, year: number | null, source: string } | null
  areaKm2: { value: number } | null
  highestPoint: { name: SilhouetteNames, elevationM: number } | null
  landlocked: boolean
  drivesLeft: boolean
  areaRank?: SilhouetteRank
  populationRank?: SilhouetteRank
}

export interface SilhouettePhoto {
  url: string
  authorName: string | null
  licenseLabel: string | null
  licenseUrl: string | null
  sourcePageUrl: string | null
  landmarkNames: SilhouetteNames | null
}

export interface SilhouetteReveal {
  countryCode: string
  subregion: string
  names: SilhouetteNames
  facts: SilhouetteFacts
  flagColors: SilhouetteFlagColor[]
  center: SilhouettePoint
  bounds: SilhouetteBounds
  shapePath: string
  photo: SilhouettePhoto | null
}

export interface SilhouettePlayerStats {
  attemptedCountries: number
  solvedCountries: number
  totalCountries: number
  answerAttempts: number
  skips: number
  xpAwarded: number
}

export interface SilhouetteSession {
  stats: SilhouettePlayerStats
  current: SilhouetteQuestion | null
  solved: string[]
}

export interface SilhouetteMiss {
  countryCode: string
  distanceKm: number
  bearingDeg: number
}

export interface SilhouetteGuessResult {
  correct: boolean
  attempts: number
  xpAwarded: number
  reveal: SilhouetteReveal | null
  miss: SilhouetteMiss | null
  misses: SilhouetteMiss[]
  stats: SilhouettePlayerStats
}

export interface SilhouetteAnswer {
  reveal: SilhouetteReveal
  misses: SilhouetteMiss[]
  stats: SilhouettePlayerStats
}

export const silhouettesApi = {
  session: async (): Promise<SilhouetteSession> => {
    const { data } = await apiClient.get<SilhouetteSession>('/silhouettes/session')
    return data
  },
  startRegistered: async (): Promise<SilhouetteSession> => {
    const { data } = await apiClient.post<SilhouetteSession>('/silhouettes/session')
    return data
  },
  startGuest: async (termsVersion: string, privacyVersion: string): Promise<SilhouetteSession> => {
    const { data } = await apiClient.post<SilhouetteSession>('/silhouettes/session/guest', { termsVersion, privacyVersion })
    return data
  },
  next: async (): Promise<SilhouetteSession> => {
    const { data } = await apiClient.post<SilhouetteSession>('/silhouettes/session/next')
    return data
  },
  hint: async (round: number): Promise<SilhouetteQuestion> => {
    const { data } = await apiClient.post<SilhouetteQuestion>(`/silhouettes/rounds/${round}/hint`)
    return data
  },
  guess: async (round: number, countryCode: string): Promise<SilhouetteGuessResult> => {
    const { data } = await apiClient.post<SilhouetteGuessResult>(`/silhouettes/rounds/${round}/guess`, { countryCode })
    return data
  },
  showAnswer: async (round: number): Promise<SilhouetteAnswer> => {
    const { data } = await apiClient.post<SilhouetteAnswer>(`/silhouettes/rounds/${round}/answer`)
    return data
  },
  skip: async (round: number): Promise<SilhouetteSession> => {
    const { data } = await apiClient.post<SilhouetteSession>(`/silhouettes/rounds/${round}/skip`)
    return data
  },
  country: async (countryCode: string): Promise<SilhouetteReveal> => {
    const { data } = await apiClient.get<SilhouetteReveal>(`/silhouettes/countries/${countryCode}`)
    return data
  },
}
