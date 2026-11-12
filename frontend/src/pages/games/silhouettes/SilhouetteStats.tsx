import { Globe2, PenLine, Sparkles } from 'lucide-react'
import type { SilhouettePlayerStats } from '@/api/silhouettes'
import type { TranslationKeys } from '@/i18n'
import { interpolate } from '@/utils/format'
import { GlobeGameStats } from '../globe-game/GlobeGameStats'
import { SILHOUETTE_PERCENT } from './silhouetteGameConstants'

interface SilhouetteStatsProps {
  copy: TranslationKeys['silhouetteGame']
  stats: SilhouettePlayerStats | null
}

const EMPTY = '—'

export function SilhouetteStats({ copy, stats }: SilhouetteStatsProps) {
  const share = stats && stats.totalCountries > 0 ? (stats.solvedCountries / stats.totalCountries) * SILHOUETTE_PERCENT : 0

  return <GlobeGameStats label={copy.statsLabel} progress={share} items={[
    {
      value: stats ? interpolate(copy.paintedOf, { solved: stats.solvedCountries, total: stats.totalCountries }) : EMPTY,
      label: copy.stats.painted, icon: Globe2, tone: 'bg-cyan-300/12 text-cyan-200',
    },
    { value: stats ? stats.answerAttempts.toLocaleString() : EMPTY, label: copy.stats.guesses, icon: PenLine, tone: 'bg-white/[.06] text-white/70' },
    { value: stats ? stats.xpAwarded.toLocaleString() : EMPTY, label: copy.stats.xp, icon: Sparkles, tone: 'bg-amber-300/12 text-river-silt' },
  ]} />
}
