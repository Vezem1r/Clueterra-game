import { apiClient } from './client'

export type RiverClueSlot = 'GEOGRAPHY' | 'PLACE' | 'NAME'
export type RiverHintKind = 'GEOGRAPHY' | 'CITY' | 'TRIBUTARY' | 'COUNTRIES' | 'NAME' | 'LENGTH'
export type RiverDifficulty = 'EASY' | 'MEDIUM' | 'HARD'

export interface RiverPlace {
  name: string
  lng: number
  lat: number
}

export interface RiverFact {
  kind: RiverHintKind
  text: string
  place?: RiverPlace | null
}

export interface RiverHint extends RiverFact {
  position: number
}

export type RiverPosition = [number, number]

export interface RiverCourse {
  bbox: [number, number, number, number]
  coordinates: RiverPosition[][]
}

export interface RiverQuestion {
  id: number
  difficulty: RiverDifficulty
  imageUrl: string
  course: RiverCourse
  revealedHints: RiverHint[]
  attempts: number
  hintsUsed: number
  maximumXp: number
  cluePenaltyXp: number
  baseXp: number
  roundNumber: number
}

export interface RiverPhoto {
  url: string
  authorName: string | null
  licenseLabel: string | null
  licenseUrl: string | null
  sourcePageUrl: string | null
}

export interface RiverReveal {
  name: string
  countryCode: string
  lengthKm: number
  mapImageUrl: string
  facts: RiverFact[]
  photo: RiverPhoto | null
  wikipediaUrl: string | null
  wikidataUrl: string | null
}

export interface RiverGuessResult {
  correct: boolean
  attempts: number
  xpAwarded: number
  reveal: RiverReveal | null
  stats: RiverPlayerStats
}

export interface RiverAnswer {
  reveal: RiverReveal
  stats: RiverPlayerStats
}

export interface RiverSession {
  stats: RiverPlayerStats
  current: RiverQuestion | null
}

export interface RiverPlayerStats {
  attemptedRivers: number
  solvedRivers: number
  answerAttempts: number
  skips: number
  xpAwarded: number
  currentQuestionId: number | null
}

export const riversApi = {
  session: async (): Promise<RiverSession> => {
    const { data } = await apiClient.get<RiverSession>('/rivers/session')
    return data
  },
  startRegistered: async (): Promise<RiverQuestion> => {
    const { data } = await apiClient.post<RiverQuestion>('/rivers/session')
    return data
  },
  startGuest: async (termsVersion: string, privacyVersion: string): Promise<RiverQuestion> => {
    const { data } = await apiClient.post<RiverQuestion>('/rivers/session/guest', { termsVersion, privacyVersion })
    return data
  },
  next: async (): Promise<RiverQuestion> => {
    const { data } = await apiClient.post<RiverQuestion>('/rivers/session/next')
    return data
  },
  hint: async (questionId: number): Promise<RiverQuestion> => {
    const { data } = await apiClient.post<RiverQuestion>(`/rivers/questions/${questionId}/hint`)
    return data
  },
  guess: async (questionId: number, answer: string): Promise<RiverGuessResult> => {
    const { data } = await apiClient.post<RiverGuessResult>(`/rivers/questions/${questionId}/guess`, { answer })
    return data
  },
  showAnswer: async (questionId: number): Promise<RiverAnswer> => {
    const { data } = await apiClient.post<RiverAnswer>(`/rivers/questions/${questionId}/answer`)
    return data
  },
  skip: async (questionId: number): Promise<RiverQuestion> => {
    const { data } = await apiClient.post<RiverQuestion>(`/rivers/questions/${questionId}/skip`)
    return data
  },
  searchNames: async (q: string): Promise<string[]> => {
    const { data } = await apiClient.get<string[]>('/rivers/names', { params: { q } })
    return data
  },
}
