import { Crown, Globe2, Sparkles } from 'lucide-react'
import type { QuizPlayerStats } from '@/api/quiz'
import type { TranslationKeys } from '@/i18n'
import { interpolate } from '@/utils/format'
import { GlobeGameStats } from '../globe-game/GlobeGameStats'
import { QUIZ_PERCENT } from './quizGameConstants'

interface QuizStatsProps {
  copy: TranslationKeys['quizGame']
  stats: QuizPlayerStats | null
}

const EMPTY = '—'

export function QuizStats({ copy, stats }: QuizStatsProps) {
  const share = stats && stats.totalLevels > 0 ? (stats.completedLevels / stats.totalLevels) * QUIZ_PERCENT : 0

  return <GlobeGameStats label={copy.statsLabel} progress={share} items={[
    {
      value: stats ? interpolate(copy.exploredOf, { explored: stats.exploredCountries, total: stats.totalCountries }) : EMPTY,
      label: copy.stats.explored, icon: Globe2, tone: 'bg-orange-200/12 text-orange-100',
    },
    {
      value: stats ? stats.masteredCountries.toLocaleString() : EMPTY,
      label: copy.stats.mastered, icon: Crown, tone: 'bg-amber-300/15 text-amber-100',
    },
    { value: stats ? stats.xpAwarded.toLocaleString() : EMPTY, label: copy.stats.xp, icon: Sparkles, tone: 'bg-amber-300/12 text-river-silt' },
  ]} />
}
