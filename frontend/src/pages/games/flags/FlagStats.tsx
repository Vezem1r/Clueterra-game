import { Flag, PenLine, Sparkles } from 'lucide-react'
import type { FlagPlayerStats } from '@/api/flags'
import type { TranslationKeys } from '@/i18n'
import { interpolate } from '@/utils/format'
import { GlobeGameStats } from '../globe-game/GlobeGameStats'
import { FLAG_PERCENT } from './flagGameConstants'

interface FlagStatsProps {
  copy: TranslationKeys['flagGame']
  stats: FlagPlayerStats | null
}

const EMPTY = '—'

export function FlagStats({ copy, stats }: FlagStatsProps) {
  const share = stats && stats.totalFlags > 0 ? (stats.solvedFlags / stats.totalFlags) * FLAG_PERCENT : 0

  return <GlobeGameStats label={copy.statsLabel} progress={share} items={[
    {
      value: stats ? interpolate(copy.raisedOf, { solved: stats.solvedFlags, total: stats.totalFlags }) : EMPTY,
      label: copy.stats.raised, icon: Flag, tone: 'bg-amber-200/12 text-amber-100',
    },
    { value: stats ? stats.answerAttempts.toLocaleString() : EMPTY, label: copy.stats.guesses, icon: PenLine, tone: 'bg-white/[.06] text-white/70' },
    { value: stats ? stats.xpAwarded.toLocaleString() : EMPTY, label: copy.stats.xp, icon: Sparkles, tone: 'bg-amber-300/12 text-river-silt' },
  ]} />
}
