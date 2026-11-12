import { Compass, Globe, Map } from 'lucide-react'

import type { TranslationKeys } from '@/i18n/en'
import type { ChallengeDto } from '@/types'
import type { HomeDifficultyOption, HomeDifficultyValue } from './types'

const DEFAULT_COUNTRIES = 5
const DEFAULT_HINTS = 5

export const DIFFICULTY_LEVELS = [
  {
    value: 'EASY' as HomeDifficultyValue,
    labelKey: 'easy' as const,
    descKey: 'easyDesc' as const,
    goalKey: 'modeGoalEasy' as const,
    Icon: Globe,
    accent: '#34d399',
    grad: 'linear-gradient(135deg,#34d399,#2dd4bf)',
    shadow: '0 14px 40px rgba(52,211,153,.24)',
    bg: 'rgba(52,211,153,.12)',
  },
  {
    value: 'MEDIUM' as HomeDifficultyValue,
    labelKey: 'normal' as const,
    descKey: 'normalDesc' as const,
    goalKey: 'modeGoalNormal' as const,
    Icon: Map,
    accent: '#a78bfa',
    grad: 'linear-gradient(135deg,#a78bfa,#818cf8)',
    shadow: '0 14px 40px rgba(167,139,250,.24)',
    bg: 'rgba(167,139,250,.12)',
  },
  {
    value: 'HARD' as HomeDifficultyValue,
    labelKey: 'hard' as const,
    descKey: 'hardDesc' as const,
    goalKey: 'modeGoalHard' as const,
    Icon: Compass,
    accent: '#fb7185',
    grad: 'linear-gradient(135deg,#fb7185,#f472b6)',
    shadow: '0 14px 40px rgba(251,113,133,.24)',
    bg: 'rgba(251,113,133,.12)',
  },
] as const

export function buildDifficultyOptions(
  challenges: ChallengeDto[],
  isAuthenticated: boolean,
  t: TranslationKeys,
): HomeDifficultyOption[] {
  return DIFFICULTY_LEVELS.map((difficulty) => {
    const challenge = challenges.find((candidate) => candidate.level === difficulty.value)
    const countries = challenge?.totalCountries ?? DEFAULT_COUNTRIES
    const totalHints = challenge?.totalHints ?? countries * DEFAULT_HINTS

    return {
      value: difficulty.value,
      label: t.home[difficulty.labelKey],
      description: t.home[difficulty.descKey].replace('{count}', String(countries)),
      goal: t.home[difficulty.goalKey],
      accent: difficulty.accent,
      bg: difficulty.bg,
      grad: difficulty.grad,
      shadow: difficulty.shadow,
      Icon: difficulty.Icon,
      countries,
      totalHints,
      hintsPerCountry: countries > 0 ? Math.round(totalHints / countries) || DEFAULT_HINTS : DEFAULT_HINTS,
      locked: !isAuthenticated && difficulty.value !== 'EASY',
      continentCount: challenge?.continentCount ?? null,
      dataSourceCount: challenge?.dataSourceCount ?? null,
      categoryCount: challenge?.categoryCount ?? null,
      avgXp: challenge?.avgXp ?? null,
    }
  })
}
