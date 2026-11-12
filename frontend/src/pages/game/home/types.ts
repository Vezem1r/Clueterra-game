import type { LucideIcon } from 'lucide-react'
import type { GameSessionSummary, ProfileDto } from '@/types'

export type HomeDifficultyValue = 'EASY' | 'MEDIUM' | 'HARD'
export type HomePanelMode = 'new' | 'continue' | 'completed'

export interface HomeDifficultyOption {
  value: HomeDifficultyValue
  label: string
  description: string
  accent: string
  bg: string
  goal: string
  grad: string
  shadow: string
  countries: number
  totalHints: number
  hintsPerCountry: number
  Icon: LucideIcon
  locked: boolean
  continentCount: number | null
  dataSourceCount: number | null
  categoryCount: number | null
  avgXp: number | null
}

export interface HomeSummaryContext {
  profile: ProfileDto | null
  summary: GameSessionSummary | null
}
