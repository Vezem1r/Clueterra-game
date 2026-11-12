import { apiClient } from './client'
import type { SilhouetteNames } from './silhouettes'

export type FlagClueKind = 'REGION' | 'INITIAL' | 'CAPITAL'

export interface FlagRegionHint {
  region: string
  subregion: string
}

export interface FlagHints {
  region: FlagRegionHint | null
  initials: SilhouetteNames | null
  capitals: SilhouetteNames[] | null
}

export interface FlagClue {
  kind: FlagClueKind
  costXp: number
}

export interface FlagQuestion {
  round: number
  tier: number
  flagUrl: string
  hints: FlagHints
  clues: FlagClue[]
  hintsUsed: number
  attempts: number
  maximumXp: number
  baseXp: number
  misses: string[]
}

export interface FlagReveal {
  countryCode: string
  region: string
  subregion: string
  names: SilhouetteNames
  capitals: SilhouetteNames[] | null
}

export interface FlagPlayerStats {
  attemptedFlags: number
  solvedFlags: number
  totalFlags: number
  answerAttempts: number
  skips: number
  xpAwarded: number
}

export interface FlagSession {
  stats: FlagPlayerStats
  current: FlagQuestion | null
  solved: string[]
}

export interface FlagGuessResult {
  correct: boolean
  attempts: number
  xpAwarded: number
  reveal: FlagReveal | null
  misses: string[]
  stats: FlagPlayerStats
}

export interface FlagAnswer {
  reveal: FlagReveal
  misses: string[]
  stats: FlagPlayerStats
}

export const flagsApi = {
  session: async (): Promise<FlagSession> => {
    const { data } = await apiClient.get<FlagSession>('/flag-game/session')
    return data
  },

  startRegistered: async (): Promise<FlagSession> => {
    const { data } = await apiClient.post<FlagSession>('/flag-game/session')
    return data
  },

  startGuest: async (termsVersion: string, privacyVersion: string): Promise<FlagSession> => {
    const { data } = await apiClient.post<FlagSession>('/flag-game/session/guest', { termsVersion, privacyVersion })
    return data
  },

  next: async (): Promise<FlagSession> => {
    const { data } = await apiClient.post<FlagSession>('/flag-game/session/next')
    return data
  },

  hint: async (round: number): Promise<FlagQuestion> => {
    const { data } = await apiClient.post<FlagQuestion>(`/flag-game/rounds/${round}/hint`)
    return data
  },

  guess: async (round: number, countryCode: string): Promise<FlagGuessResult> => {
    const { data } = await apiClient.post<FlagGuessResult>(`/flag-game/rounds/${round}/guess`, { countryCode })
    return data
  },

  showAnswer: async (round: number): Promise<FlagAnswer> => {
    const { data } = await apiClient.post<FlagAnswer>(`/flag-game/rounds/${round}/answer`)
    return data
  },

  skip: async (round: number): Promise<FlagSession> => {
    const { data } = await apiClient.post<FlagSession>(`/flag-game/rounds/${round}/skip`)
    return data
  },

  country: async (countryCode: string): Promise<FlagReveal> => {
    const { data } = await apiClient.get<FlagReveal>(`/flag-game/countries/${countryCode}`)
    return data
  },
}
