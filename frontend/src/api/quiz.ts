import { apiClient } from './client'
import type { SilhouetteNames } from './silhouettes'

export type QuizKind =
  | 'FLAG'
  | 'CAPITAL'
  | 'NEIGHBOUR'
  | 'CURRENCY'
  | 'POPULATION'
  | 'AREA'
  | 'LEFT_DRIVING'
  | 'DOMAIN'
  | 'LANDLOCKED'
  | 'OUTLINE'
  | 'COASTLINE'
  | 'TOURISTS'
  | 'PEAK'
  | 'NEIGHBOUR_CAPITAL'
  | 'HIGHEST_POINT'
  | 'NOT_NEIGHBOUR'
  | 'LIFE_EXPECTANCY'
  | 'WEALTH'
  | 'HERITAGE'
  | 'NEIGHBOUR_COUNT'
  | 'FOREST'
  | 'MEDIAN_AGE'

export type QuizMetric =
  | 'POPULATION'
  | 'AREA'
  | 'PEAK'
  | 'COASTLINE'
  | 'TOURISTS'
  | 'LIFE_EXPECTANCY'
  | 'WEALTH'
  | 'HERITAGE'
  | 'FOREST'
  | 'MEDIAN_AGE'

export type QuizTravelMode = 'DRIVE' | 'FLY'
export type QuizOptionType = 'COUNTRY' | 'PLACE' | 'CURRENCY' | 'FLAG' | 'SHAPE' | 'NUMBER' | 'TEXT'

export interface QuizCountry {
  countryCode: string
  names: SilhouetteNames
  region: string
  subregion: string
  tier: number
}

export interface QuizOption {
  type: QuizOptionType
  countryCode?: string
  names?: SilhouetteNames
  flagUrl?: string
  shape?: string
  value?: number
  text?: string
}

export type QuizFact =
  | { type: 'POPULATION', value: number, year?: number }
  | { type: 'AREA', value: number }
  | { type: 'HIGHEST_POINT', names: SilhouetteNames, elevation: number }
  | { type: 'CAPITAL', names: SilhouetteNames }
  | { type: 'CURRENCY', names: SilhouetteNames, code: string }
  | { type: 'DRIVES_LEFT', iso?: string }
  | { type: 'LANDLOCKED' }
  | { type: 'NEIGHBOURS', countries: string[] }
  | { type: 'REGION', subregion: string }
  | { type: 'VALUES', metric: QuizMetric, values: number[] }

export interface QuizAnswerResult {
  selectedIndex: number
  answerIndex: number
  correct: boolean
  xpAwarded: number
  fact: QuizFact
}

export interface QuizQuestion {
  position: number
  kind: QuizKind
  subject: { countryCode: string, names: SilhouetteNames } | null
  options: QuizOption[]
  answer: QuizAnswerResult | null
}

export interface QuizDestination {
  country: QuizCountry
  mode: QuizTravelMode
  level: number
  route: string[]
}

export interface QuizStop {
  stop: number
  country: QuizCountry
  level: number
  levels: number
  arrivalMode: QuizTravelMode | null
  fromCountryCode: string | null
  questions: QuizQuestion[]
  next: QuizDestination | null
  finished: boolean
  answerXp: number
}

export interface QuizCountryLevel {
  countryCode: string
  level: number
  levels: number
}

export interface QuizVisit {
  stop: number
  countryCode: string
  fromCountryCode: string | null
  mode: QuizTravelMode | null
  route: string[]
  level: number
}

export interface QuizPlayerStats {
  exploredCountries: number
  masteredCountries: number
  totalCountries: number
  completedLevels: number
  totalLevels: number
  answeredQuestions: number
  correctAnswers: number
  xpAwarded: number
}

export interface QuizSession {
  stats: QuizPlayerStats
  current: QuizStop | null
  countries: QuizCountryLevel[]
  trail: QuizVisit[]
  complete: boolean
}

export interface QuizAnswer {
  answer: QuizAnswerResult
  stop: QuizStop
  stats: QuizPlayerStats
  complete: boolean
}

export const quizApi = {
  session: async (): Promise<QuizSession> => {
    const { data } = await apiClient.get<QuizSession>('/quiz-game/session')
    return data
  },

  startRegistered: async (): Promise<QuizSession> => {
    const { data } = await apiClient.post<QuizSession>('/quiz-game/session')
    return data
  },

  startGuest: async (termsVersion: string, privacyVersion: string): Promise<QuizSession> => {
    const { data } = await apiClient.post<QuizSession>('/quiz-game/session/guest', { termsVersion, privacyVersion })
    return data
  },

  answer: async (stop: number, position: number, option: number): Promise<QuizAnswer> => {
    const { data } = await apiClient.post<QuizAnswer>(`/quiz-game/stops/${stop}/questions/${position}/answer`, { option })
    return data
  },

  travel: async (stop: number): Promise<QuizSession> => {
    const { data } = await apiClient.post<QuizSession>(`/quiz-game/stops/${stop}/travel`)
    return data
  },
}
